import gitbucket.core.plugin.PluginRegistry
import gitbucket.core.service.SystemSettingsService
import io.github.gitbucket.solidbase.model.Version
import io.github.kounoike.ldapserver.controller.HelloWorldController
import javax.servlet.ServletContext
import ldapserver.ldap.LdapServer

class Plugin extends gitbucket.core.plugin.Plugin {
  override val pluginId: String = "ldapserver-poc"
  override val pluginName: String = "ldapserver(Proof of Concept) Plugin"
  override val description: String = "LDAP server feature for GitBucket plug-in"
  override val versions: List[Version] = List(new Version("1.0.0"))

  override val controllers = Seq(
    "/*" -> new HelloWorldController()
  )

  override def initialize(registry: PluginRegistry, context: ServletContext, settings: SystemSettingsService.SystemSettings): Unit = {
    super.initialize(registry, context, settings)
    LdapServer.init()
  }

  override def shutdown(registry: PluginRegistry, context: ServletContext, settings: SystemSettingsService.SystemSettings): Unit = {
    super.shutdown(registry, context, settings)
    LdapServer.stop()
  }
}
