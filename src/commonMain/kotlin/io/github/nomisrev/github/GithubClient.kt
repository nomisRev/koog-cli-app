package io.github.nomisrev.github

import com.xemantic.ai.tool.schema.meta.Description
import io.github.nomisrev.tools.Tool
import io.github.nomisrev.tools.asTool
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

fun GithubHttpClient(token: String? = null) = HttpClient {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    defaultRequest {
        header(HttpHeaders.Authorization, "Bearer $token")
        header(HttpHeaders.Accept, "application/vnd.github+json")
        header("X-GitHub-Api-Version", "2022-11-28")
    }
}

/**
 * Minimal GitHub client to fetch Pull Request text (title/body) and comments.
 *
 * It relies on the GitHub REST API v3 and works with an optional token from the
 * GITHUB_TOKEN environment variable. When set, it increases rate limits and allows
 * private repo access if permitted by the token.
 */
class GithubClient(private val client: HttpClient) {
    fun tools(): List<Tool<*, *>> = listOf(
        ::getPullRequest.asTool("Get pull request from github"),
        ::getPullRequestComments.asTool("Get pull request comments from github")
    )

    suspend fun getPullRequest(input: GetPullRequestCommentsInput): PullRequestDetails {
        val pr: PullRequestResponse = client
            .get("https://api.github.com/repos/${input.owner}/${input.repo}/pulls/${input.number}")
            .body()
        return PullRequestDetails(
            number = pr.number,
            title = pr.title,
            body = pr.body ?: "",
            author = pr.user?.login ?: "",
            url = pr.htmlUrl ?: ""
        )
    }

    suspend fun getPullRequestComments(input: GetPullRequestCommentsInput): List<Comment> {
        val issueComments: List<IssueCommentResponse> = client
            .get("https://api.github.com/repos/${input.owner}/${input.owner}/issues/${input.number}/comments")
            .body()

        val reviewComments: List<ReviewCommentResponse> = client
            .get("https://api.github.com/repos/${input.owner}/${input.repo}/pulls/${input.number}/comments")
            .body()

        val unifiedIssue = issueComments.map {
            Comment(
                id = it.id,
                author = it.user?.login ?: "",
                body = it.body ?: "",
                createdAt = it.createdAt ?: "",
                type = CommentType.Issue
            )
        }
        val unifiedReview = reviewComments.map {
            Comment(
                id = it.id,
                author = it.user?.login ?: "",
                body = it.body ?: "",
                createdAt = it.createdAt ?: "",
                type = CommentType.Review
            )
        }
        return (unifiedIssue + unifiedReview).sortedBy { it.createdAt }
    }

    @Description("Get Pull Request Input")
    @Serializable
    data class GetPullRequestInput(
        @Description("")
        val owner: String,
        @Description("")
        val repo: String,
        @Description("")
        val number: Int
    )


    @Description("Get Pull Request Comments Input")
    @Serializable
    class GetPullRequestCommentsInput(
        @Description("")
        val owner: String,
        @Description("")
        val repo: String,
        @Description("")
        val number: Int
    )

    @Description("Pull Request Details")
    @Serializable
    data class PullRequestDetails(
        @Description("Pull Request Number")
        val number: Int,
        @Description("Pull Request Title")
        val title: String,
        @Description("Pull Request Body")
        val body: String,
        @Description("Pull Request Author")
        val author: String,
        @Description("Pull Request URL")
        val url: String
    )

    @Description("Comment")
    @Serializable
    data class Comment(
        @Description("Comment ID")
        val id: Long,
        @Description("Comment Author")
        val author: String,
        @Description("Comment Body")
        val body: String,
        @Description("Comment Creation Date")
        val createdAt: String,
        @Description("Comment Type")
        val type: CommentType
    )

    @Serializable
    enum class CommentType { Issue, Review }

    // Internal API response models (partial; unknown fields ignored)
    @Serializable
    private data class PullRequestResponse(
        val number: Int,
        val title: String,
        val body: String? = null,
        @SerialName("html_url") val htmlUrl: String? = null,
        val user: User? = null
    )

    @Serializable
    private data class IssueCommentResponse(
        val id: Long,
        val body: String? = null,
        @SerialName("created_at") val createdAt: String? = null,
        val user: User? = null
    )

    @Serializable
    private data class ReviewCommentResponse(
        val id: Long,
        val body: String? = null,
        @SerialName("created_at") val createdAt: String? = null,
        val user: User? = null
    )

    @Serializable
    private data class User(val login: String? = null)
}
