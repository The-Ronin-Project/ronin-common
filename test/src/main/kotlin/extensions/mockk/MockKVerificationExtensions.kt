import io.mockk.Called
import io.mockk.verify

/**
 * Easy way to verify something wasn't called
 *
 *
 */
fun Any.wasNotCalled() {
    verify { this@wasNotCalled wasNot Called }
}

fun List<Any>.wasNotCalled() {
    verify { this@wasNotCalled wasNot Called }
}
