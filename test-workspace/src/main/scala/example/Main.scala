
import java.lang.management.ManagementFactory

object DisconnectSession { 
    def main(args: Array[String]): Unit = {
        val name = ManagementFactory.getRuntimeMXBean().getName()
        var i = 0
        val const = 200
        while(true){ 
            
            Thread.sleep(const) 
            i += const
            println(s"$name")
         }
    }
}


