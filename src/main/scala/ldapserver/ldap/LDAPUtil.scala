package ldapserver.ldap

import org.apache.directory.api.ldap.model.password.BCrypt

object LDAPUtil {
  val ldapName: String = "gitbucket"
  val baseDnName: String = "o=gitbucket"

  val usersDn: String = s"ou=Users,$baseDnName"
  val groupsDn: String = s"ou=Groups,$baseDnName"

  val systemAdmin: String = "uid=admin,ou=system"

  def encodePassword(plain: String): String = {
    val hashed = BCrypt.hashPw(plain, BCrypt.genSalt())
    "{CRYPT}" + hashed
  }

  def checkPassword(encodedPassword: String, password: String): Boolean = {
    val re = "(\\{BCRYPT\\})(.*)".r
    encodedPassword match {
      case re(_, p) =>
        p == BCrypt.checkPw(password, p)
      case _ =>
        encodedPassword == password
    }
  }
}
