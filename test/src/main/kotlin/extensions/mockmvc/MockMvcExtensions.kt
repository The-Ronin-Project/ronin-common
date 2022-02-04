import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.setup.MockMvcBuilders

fun ResultActionsDsl.statusOk() = andExpect { status { isOk() } }

fun ResultActionsDsl.statusNotFound() = andExpect { status { isNotFound() } }

fun ResultActionsDsl.expectJsonContent(content: String) = andExpect { content { json(content) } }

fun ResultActionsDsl.expectStringContent(content: String) = andExpect { content { string(content) } }

fun ResultActionsDsl.expectEmptyContent() = andExpect { content { string("") } }

fun <T> buildMockMvc(vararg controller: T) = MockMvcBuilders.standaloneSetup(controller).build()
