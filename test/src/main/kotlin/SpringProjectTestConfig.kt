import io.kotest.core.config.AbstractProjectConfig
import io.kotest.extensions.spring.SpringExtension

open class SpringProjectTestConfig : AbstractProjectConfig() {
    override fun extensions() = listOf(SpringExtension)
}
