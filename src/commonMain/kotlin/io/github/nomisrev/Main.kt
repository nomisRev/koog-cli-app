package io.github.nomisrev

import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import io.github.nomisrev.github.GithubClient
import io.github.nomisrev.github.GithubHttpClient
import kotlinx.coroutines.Dispatchers

suspend fun main(args: Array<String>) =
    Hello().main(args)

class Hello : SuspendingCliktCommand() {
    val apiKey: String by option(envvar = "OPENAI_API_KEY").required().help("OpenAI API key")
    val githubApiKey: String? by option(envvar = "GITHUB_API_KEY").help("Github API key")

    override suspend fun run(): Unit = with(Dispatchers.Default) {
        println("Hello Koog App: loading OpenAI:$apiKey")
        val module = Module(apiKey, githubApiKey)
        module.github.getPullRequest(
            GithubClient.PullRequestInput(
                "nomisRev",
                "kotlinx-serialization-jsonpath",
                75
            )
        ).also { println(it) }

//

    }
}

class Module(apiKey: String, githubApiKey: String?) : AutoCloseable {
    val executor = SingleLLMPromptExecutor(OpenAILLMClient(apiKey))
    val client = GithubHttpClient(githubApiKey)
    val github = GithubClient(client)

    override fun close() {
        client.close()
    }
}
