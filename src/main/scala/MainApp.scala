import com.google.common.io.Files
import com.kjetland.ddsl.DdslClientImpl
import com.kjetland.ddsl.model.{ServiceLocation, ClientId, ServiceId, ServiceRequest}
import com.sun.org.apache.xalan.internal.utils.ConfigurationError
import java.io.{FileReader, StringWriter, FileInputStream, File}
import java.net.URL
import java.util.Properties
import org.apache.velocity.app.VelocityEngine
import org.apache.velocity.VelocityContext
import org.slf4j.LoggerFactory
import util.Properties


class ConfigError(msg:String) extends Exception(msg)

case class Config(
  pathToConfigDestination:File,
  pathToConfigTemplate:File,
  triggerReloadingCommand:String,
  secondsBetweenChecks:Int,
  ddslEnvironment:String,
  ddslServiceName:String,
  ddslServiceVersion:String,
  ddslServiceType:String,
  ddslClientName:String,
  ddslClientVersion:String

)

class Props(val configFile:File) extends Properties {
  val logger = LoggerFactory.getLogger(getClass)

  readConfig()

  private def readConfig() {
    try {
      logger.info("Reading config from " + configFile)
      load(new FileInputStream(configFile))
    } catch {
      case e:Exception => throw new Exception("Error loading config from " + configFile, e)
    }
  }

  def readString(name:String):String = {
    val v = getProperty(name)
    if ( v == null ) throw new ConfigError("Missing config param from "+configFile+": " + name)
    v
  }

  def readInt(name:String):Int = {
    readString(name).toInt
  }

}

object MainApp {
  val logger = LoggerFactory.getLogger(getClass)

  lazy val client = new DdslClientImpl()

  def main(args:Array[String]) {

    try {

      logger.info("Starting config-syncing with ddsl")

      var prevGeneratedConfig = ""

      while ( true ) {

        val config = readConfig()

        generateConfig(config) match {
          case Some(newGeneratedConfig:String) => {
            if ( !newGeneratedConfig.equals(prevGeneratedConfig)) {
              if ( writeNewConfig(config, newGeneratedConfig) ) {
                // trigger reloading
                if ( triggerReloading(config) ) {
                  prevGeneratedConfig = newGeneratedConfig
                }
              }

            }
          }
          case None => None
        }

        val seconds = config.secondsBetweenChecks
        println("Sleeping " + seconds)
        Thread.sleep(seconds * 1000)

      }



    } catch {
      case e : ConfigError => logger.error(e.getMessage)
      case e:Throwable => logger.error("Something went wrong", e)
    }
  }

  def triggerReloading(config:Config):Boolean =  {
    logger.info("Triggering reloading with cmd: " + config.triggerReloadingCommand)
    try {
      val p:Process = Runtime.getRuntime.exec(config.triggerReloadingCommand)
      val errorLevel = p.waitFor()
      logger.info("Triggered reloading. errorCode: " + errorLevel)

      return true
    } catch {
      case e:Exception => logger.error("Error triggering reloading", e)
    }
    return false
  }

  def writeNewConfig(config:Config, newGenericConfig:String) : Boolean =  {
    logger.info("Writing new config to " + config.pathToConfigDestination)
    try {
      Files.write(newGenericConfig.getBytes("utf-8"), config.pathToConfigDestination)
      return true
    } catch {
      case e:Exception => logger.error("Error writing new config to " + config.pathToConfigDestination, e)
    }
    return false
  }

  def generateConfig( config:Config) : Option[String] = {
    try {
      val newSLList = getServiceLocations(config)

      val generatedConfigString = TemplateStuff.generate(config.pathToConfigTemplate, newSLList.toList)

      return Some(generatedConfigString)
    } catch {
      case e:Exception => logger.error("Got error while generateConfig", e)
    }
    return None
  }

  def getServiceLocations( config:Config) : Array[ServiceLocation] = {
    client.getServiceLocations( ServiceRequest(ServiceId(config.ddslEnvironment, config.ddslServiceType, config.ddslServiceName, config.ddslServiceVersion), ClientId(config.ddslEnvironment, config.ddslClientName, config.ddslClientVersion, null)))
  }



  def readConfig() : Config = {
    val configFile = new File("config.properties")
    if ( !configFile.exists()) throw new ConfigError("Cannot find " + configFile)
    val props = new Props(configFile)

    Config(
      new File(props.readString("pathToConfigDestination")),
      new File(props.readString("pathToConfigTemplate")),
      props.readString("triggerReloadingCommand"),
      props.readInt("secondsBetweenChecks"),
      props.readString("ddsl.environment"),
      props.readString("ddsl.serviceName"),
      props.readString("ddsl.serviceVersion"),
      props.readString("ddsl.serviceType"),
      props.readString("ddsl.clientName"),
      props.readString("ddsl.clientVersion")
    )


  }


}

case class SL(host:String, port:Int)

object TemplateStuff {

  private val ve = new VelocityEngine()
  ve.init()

  def generate(file:File, serviceLocations:List[ServiceLocation]):String = {
    import scala.collection.JavaConversions._


    val slList = serviceLocations.map( {sl : ServiceLocation => {
      val url = new URL(sl.url)
      SL(url.getHost, url.getPort)
    }})

    val context = new VelocityContext()
    val javaServiceLocationsList : java.util.List[SL] = slList
    context.put("serviceLocations", javaServiceLocationsList)
    val sw = new StringWriter()
    val in = new FileReader(file)
    try {
      ve.evaluate(context, sw, "", in)
      return sw.toString
    }
    finally {
      if ( in != null) in.close()
    }
  }

}
