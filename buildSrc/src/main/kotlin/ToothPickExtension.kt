import org.gradle.api.model.ObjectFactory
import java.io.File

open class ToothPickExtension(@Suppress("UNUSED_PARAMETER") objects: ObjectFactory) {

    var minecraftVersion: String = "1.16.2"
    var serverMappings: String = "https://launcher.mojang.com/v1/objects/3405a0f2c0ccacd36a8158ae29b16eaa915b5d28/server.txt"
    lateinit var forkName: String
    lateinit var upstreamName: String
    lateinit var groupId: String

    lateinit var subProjects :Map<String, List<File>>
}
