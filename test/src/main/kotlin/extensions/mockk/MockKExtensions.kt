import io.mockk.mockk
import kotlin.reflect.KClass

/**
 * Wrapper around manual mock creation.  By default, mocks require ALL behavior to be stubbed out.
 * Relaxed mocks, however, use whatever default value is common for the return type (and are MUCH more commonly used)
 */
inline fun <reified T : Any> relaxedMock(
    name: String? = null,
    vararg moreInterfaces: KClass<*>,
    relaxUnitFun: Boolean = false,
    block: T.() -> Unit = {}
) = mockk(
    name = name,
    relaxed = true,
    moreInterfaces = moreInterfaces,
    relaxUnitFun = relaxUnitFun,
    block = block
)
