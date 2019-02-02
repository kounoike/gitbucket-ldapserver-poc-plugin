package ldapserver.ldap

import org.apache.directory.server.constants.ServerDNConstants
import org.apache.directory.api.ldap.model.entry.{DefaultAttribute, DefaultModification, ModificationOperation}
import org.apache.directory.api.ldap.model.name.Dn
import org.apache.directory.server.core.api.CoreSession
import org.apache.directory.server.protocol.shared.transport.TcpTransport
import org.slf4j.{Logger, LoggerFactory}

case class LdapServer() {}

object LdapServer extends LdapServer {
  val logger: Logger = LoggerFactory.getLogger(getClass)

  val dsFactory = new LdapDirectoryServiceFactory()
  dsFactory.init(LDAPUtil.ldapName)
  val directoryService = dsFactory.getDirectoryService()
  var ldapServer = new org.apache.directory.server.ldap.LdapServer()

  val ldapBindOnlyLocal: Boolean = false
  val ldapPort = 10389

  def init(): Unit = {
    logger.info("starting LDAP server")
    val bindHost = if (ldapBindOnlyLocal) "127.0.0.1" else "0.0.0.0"
    ldapServer.setTransports(new TcpTransport(bindHost, ldapPort))
    ldapServer.setDirectoryService(directoryService)
    ldapServer.start()
    logger.info("LDAP server started")
  }

  def restart(): Unit = {
    ldapServer.stop()
    ldapServer = new org.apache.directory.server.ldap.LdapServer()
    init()
  }

  def stop(): Unit = {
    logger.info("stopping LDAP server")
    ldapServer.stop()
    logger.info("stopped LDAP server")
  }

  def getAdminSession(): CoreSession = directoryService.getAdminSession()
}
