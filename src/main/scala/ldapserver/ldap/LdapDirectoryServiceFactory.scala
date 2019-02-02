package ldapserver.ldap

import java.io.File

import gitbucket.core.model.Profile.profile.blockingApi._
import gitbucket.core.service.AccountService

import scala.collection.JavaConverters._
import net.sf.ehcache.CacheManager
import net.sf.ehcache.config.{CacheConfiguration, Configuration}
import org.apache.directory.api.ldap.model.constants.SchemaConstants
import org.apache.directory.api.ldap.model.entry.{DefaultEntry, DefaultModification, Entry, ModificationOperation}
import org.apache.directory.api.ldap.model.ldif.LdifReader
import org.apache.directory.api.ldap.model.schema.comparators.NormalizingComparator
import org.apache.directory.api.ldap.schema.loader.JarLdifSchemaLoader
import org.apache.directory.api.ldap.schema.manager.impl.DefaultSchemaManager
import org.apache.directory.api.util.exception.Exceptions
import org.apache.directory.server.constants.ServerDNConstants
import org.apache.directory.server.core.DefaultDirectoryService
import org.apache.directory.server.core.api.schema.SchemaPartition
import org.apache.directory.server.core.api.{CacheService, CoreSession, DirectoryService, InstanceLayout}
import org.apache.directory.server.core.factory.{DirectoryServiceFactory, LdifPartitionFactory, PartitionFactory}
import org.apache.directory.server.i18n.I18n
import gitbucket.core.util.Directory
import ldapserver.ldap.LdapServer.directoryService
import org.apache.directory.api.ldap.model.name.Dn
import org.apache.directory.api.ldap.model.schema.AttributeType

class LdapDirectoryServiceFactory extends DirectoryServiceFactory with AccountService {
  val directoryService: DirectoryService = new DefaultDirectoryService
  directoryService.setShutdownHookEnabled(false)

  val partitionFactory: PartitionFactory = new LdifPartitionFactory

  override def init(name: String): Unit = {
    if (directoryService.isStarted) return

    directoryService.setInstanceId(name)
    directoryService.setInstanceLayout(new InstanceLayout(Directory.GitBucketHome + "/ldap"))

    // EhCache in disabled-like-mode
    val ehCacheConfig: Configuration = new Configuration()
    val defaultCache = new CacheConfiguration("default", 1).eternal(false).timeToIdleSeconds(30).timeToLiveSeconds(30)
    ehCacheConfig.addDefaultCache(defaultCache)
    directoryService.setCacheService(new CacheService(new CacheManager(ehCacheConfig)))

    // Init the schema
    val schemaManager = new DefaultSchemaManager(new JarLdifSchemaLoader())
    schemaManager.loadAllEnabled()
    schemaManager.getComparatorRegistry.asScala.foreach { comparator =>
      if (comparator.isInstanceOf[NormalizingComparator]) {
        comparator.asInstanceOf[NormalizingComparator].setOnServer()
      }
    }
    directoryService.setSchemaManager(schemaManager)
    val inMemorySchemaPartition = new InMemorySchemaPartition(schemaManager)

    val schemaPartition = new SchemaPartition(schemaManager)
    schemaPartition.setWrappedPartition(inMemorySchemaPartition)
    directoryService.setSchemaPartition(schemaPartition)

    val errors = schemaManager.getErrors
    if (errors.size > 0) {
      throw new Exception(I18n.err(I18n.ERR_317, Exceptions.printErrors(errors)))
    }

    // Init system partition
    val systemPartition = partitionFactory.createPartition(
      directoryService.getSchemaManager,
      directoryService.getDnFactory,
      "system",
      ServerDNConstants.SYSTEM_DN,
      500,
      new File(directoryService.getInstanceLayout.getPartitionsDirectory, "system")
    )
    systemPartition.setSchemaManager(directoryService.getSchemaManager)
    partitionFactory.addIndex(systemPartition, SchemaConstants.OBJECT_CLASS_AT, 100)
    directoryService.setSystemPartition(systemPartition)

    directoryService.setAccessControlEnabled(true)
    directoryService.setAllowAnonymousAccess(true)

    directoryService.startup()

    val schemaReader = new LdifReader((getClass.getResourceAsStream("/schema.ldif")))
    schemaReader.forEach { ldifEntry =>
      val entry: Entry = new DefaultEntry(schemaManager, ldifEntry.getEntry)
      directoryService.getAdminSession.add(entry)
    }

    val dataPartition = partitionFactory.createPartition(
      directoryService.getSchemaManager,
      directoryService.getDnFactory,
      LDAPUtil.ldapName,
      LDAPUtil.baseDnName,
      500,
      new File(directoryService.getInstanceLayout.getPartitionsDirectory, LDAPUtil.ldapName)
    )
    directoryService.addPartition(dataPartition)

    val session = directoryService.getAdminSession()

    if (!session.exists(LDAPUtil.baseDnName)) {
      val reader = new LdifReader(getClass.getResourceAsStream("/base.ldif"))
      reader.forEach { entry =>
        session.add(new DefaultEntry(directoryService.getSchemaManager, entry.getEntry))
      }

      val con = directoryService.getAdminSession

      val passwordRemove = new DefaultModification(ModificationOperation.REMOVE_ATTRIBUTE, "userPassword")
      val passwordAdd = new DefaultModification(
        ModificationOperation.ADD_ATTRIBUTE,
        "userPassword",
        LDAPUtil.encodePassword("ldap")
      )
      println(ServerDNConstants.ADMIN_SYSTEM_DN)
      println(LDAPUtil.encodePassword("ldap"))
      println(con.getAuthenticatedPrincipal.toString)
      println(con.getAuthenticatedPrincipal.getUserPasswords)
      val sm = con.getDirectoryService.getSchemaManager

      con.modify(
        new Dn(sm, ServerDNConstants.ADMIN_SYSTEM_DN),
        passwordRemove,
        passwordAdd
      )

      gitbucket.core.servlet.Database() withSession { implicit session =>
        getAllUsers(true).foreach{ account =>
          val entry = new DefaultEntry(sm)
          entry.setDn(new Dn(sm, s"uid=${account.userName},${LDAPUtil.usersDn}"))
          entry.add(sm.getAttributeType("objectClass"), "top", "inetOrgPerson", "person", "organizationalPerson", "simulatedMemberOfObjectClass")
          entry.add(sm.getAttributeType("uid"), account.userName)
          entry.add(sm.getAttributeType("sn"), account.userName)
          entry.add(sm.getAttributeType("cn"), account.userName)
          entry.add(sm.getAttributeType("displayName"), account.fullName)
          entry.add(sm.getAttributeType("userPassword"), "{CRYPT}" + account.password)
          entry.add(sm.getAttributeType("mail"), account.mailAddress)
          getAccountExtraMailAddresses(account.userName).foreach{ m =>
            entry.add(sm.getAttributeType("mail"), m)
          }
          println("setDn")
          println(entry)
          con.add(entry)
        }
      }
    }
  }

  override def getDirectoryService = directoryService

  override def getPartitionFactory = partitionFactory
}
