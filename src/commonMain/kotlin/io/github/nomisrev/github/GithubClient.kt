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
        json()
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

    //  "/repos/{owner}/{repo}/pulls/{pull_number}":
    //    get:
    //      summary: Get a pull request
    //      description: |-
    //        Draft pull requests are available in public repositories with GitHub Free and GitHub Free for organizations, GitHub Pro, and legacy per-repository billing plans, and in public and private repositories with GitHub Team and GitHub Enterprise Cloud. For more information, see [GitHub's products](https://docs.github.com/github/getting-started-with-github/githubs-products) in the GitHub Help documentation.
    //
    //        Lists details of a pull request by providing its number.
    //
    //        When you get, [create](https://docs.github.com/rest/pulls/pulls/#create-a-pull-request), or [edit](https://docs.github.com/rest/pulls/pulls#update-a-pull-request) a pull request, GitHub creates a merge commit to test whether the pull request can be automatically merged into the base branch. This test commit is not added to the base branch or the head branch. You can review the status of the test commit using the `mergeable` key. For more information, see "[Checking mergeability of pull requests](https://docs.github.com/rest/guides/getting-started-with-the-git-database-api#checking-mergeability-of-pull-requests)".
    //
    //        The value of the `mergeable` attribute can be `true`, `false`, or `null`. If the value is `null`, then GitHub has started a background job to compute the mergeability. After giving the job time to complete, resubmit the request. When the job finishes, you will see a non-`null` value for the `mergeable` attribute in the response. If `mergeable` is `true`, then `merge_commit_sha` will be the SHA of the _test_ merge commit.
    //
    //        The value of the `merge_commit_sha` attribute changes depending on the state of the pull request. Before merging a pull request, the `merge_commit_sha` attribute holds the SHA of the _test_ merge commit. After merging a pull request, the `merge_commit_sha` attribute changes depending on how you merged the pull request:
    //
    //        *   If merged as a [merge commit](https://docs.github.com/articles/about-merge-methods-on-github/), `merge_commit_sha` represents the SHA of the merge commit.
    //        *   If merged via a [squash](https://docs.github.com/articles/about-merge-methods-on-github/#squashing-your-merge-commits), `merge_commit_sha` represents the SHA of the squashed commit on the base branch.
    //        *   If [rebased](https://docs.github.com/articles/about-merge-methods-on-github/#rebasing-and-merging-your-commits), `merge_commit_sha` represents the commit that the base branch was updated to.
    //
    //        Pass the appropriate [media type](https://docs.github.com/rest/using-the-rest-api/getting-started-with-the-rest-api#media-types) to fetch diff and patch formats.
    //
    //        This endpoint supports the following custom media types. For more information, see "[Media types](https://docs.github.com/rest/using-the-rest-api/getting-started-with-the-rest-api#media-types)."
    //
    //        - **`application/vnd.github.raw+json`**: Returns the raw markdown body. Response will include `body`. This is the default if you do not pass any specific media type.
    //        - **`application/vnd.github.text+json`**: Returns a text only representation of the markdown body. Response will include `body_text`.
    //        - **`application/vnd.github.html+json`**: Returns HTML rendered from the body's markdown. Response will include `body_html`.
    //        - **`application/vnd.github.full+json`**: Returns raw, text, and HTML representations. Response will include `body`, `body_text`, and `body_html`.
    //        - **`application/vnd.github.diff`**: For more information, see "[git-diff](https://git-scm.com/docs/git-diff)" in the Git documentation. If a diff is corrupt, contact us through the [GitHub Support portal](https://support.github.com/). Include the repository name and pull request ID in your message.
    //      tags:
    //        - pulls
    //      operationId: pulls/get
    //      externalDocs:
    //        description: API method documentation
    //        url: https://docs.github.com/rest/pulls/pulls#get-a-pull-request
    //      parameters:
    //        - "$ref": "#/components/parameters/owner"
    //        - "$ref": "#/components/parameters/repo"
    //        - "$ref": "#/components/parameters/pull-number"
    //      responses:
    //        '200':
    //          description: Pass the appropriate [media type](https://docs.github.com/rest/using-the-rest-api/getting-started-with-the-rest-api#media-types)
    //            to fetch diff and patch formats.
    //          content:
    //            application/json:
    //              schema:
    //                "$ref": "#/components/schemas/pull-request"
    //              examples:
    //                default:
    //                  "$ref": "#/components/examples/pull-request"
    //        '304':
    //          "$ref": "#/components/responses/not_modified"
    //        '404':
    //          "$ref": "#/components/responses/not_found"
    //        '406':
    //          "$ref": "#/components/responses/unacceptable"
    //        '500':
    //          "$ref": "#/components/responses/internal_error"
    //        '503':
    //          "$ref": "#/components/responses/service_unavailable"
    //      x-github:
    //        githubCloudOnly: false
    //        enabledForGitHubApps: true
    //        category: pulls
    //        subcategory: pulls
    suspend fun getPullRequest(input: PullRequestInput): PullRequestDetails {
        val pr: PullRequestResponse = client
            .get("https://api.github.com/repos/${input.owner}/${input.repo}/pulls/${input.number}")
            .body()
        return PullRequestDetails(
            number = pr.number ?: 0,
            title = pr.title ?: "",
            body = pr.body ?: "",
            author = pr.user?.login ?: "",
            url = pr.htmlUrl ?: ""
        )
    }

    //   "/repos/{owner}/{repo}/pulls/{pull_number}/comments":
    //    get:
    //      summary: List review comments on a pull request
    //      description: |-
    //        Lists all review comments for a specified pull request. By default, review comments
    //        are in ascending order by ID.
    //
    //        This endpoint supports the following custom media types. For more information, see "[Media types](https://docs.github.com/rest/using-the-rest-api/getting-started-with-the-rest-api#media-types)."
    //
    //        - **`application/vnd.github-commitcomment.raw+json`**: Returns the raw markdown body. Response will include `body`. This is the default if you do not pass any specific media type.
    //        - **`application/vnd.github-commitcomment.text+json`**: Returns a text only representation of the markdown body. Response will include `body_text`.
    //        - **`application/vnd.github-commitcomment.html+json`**: Returns HTML rendered from the body's markdown. Response will include `body_html`.
    //        - **`application/vnd.github-commitcomment.full+json`**: Returns raw, text, and HTML representations. Response will include `body`, `body_text`, and `body_html`.
    //      tags:
    //        - pulls
    //      operationId: pulls/list-review-comments
    //      externalDocs:
    //        description: API method documentation
    //        url: https://docs.github.com/rest/pulls/comments#list-review-comments-on-a-pull-request
    //      parameters:
    //        - "$ref": "#/components/parameters/owner"
    //        - "$ref": "#/components/parameters/repo"
    //        - "$ref": "#/components/parameters/pull-number"
    //        - "$ref": "#/components/parameters/sort"
    //        - name: direction
    //          description: The direction to sort results. Ignored without `sort` parameter.
    //          in: query
    //          required: false
    //          schema:
    //            type: string
    //            enum:
    //              - asc
    //              - desc
    //        - "$ref": "#/components/parameters/since"
    //        - "$ref": "#/components/parameters/per-page"
    //        - "$ref": "#/components/parameters/page"
    //      responses:
    //        '200':
    //          description: Response
    //          content:
    //            application/json:
    //              schema:
    //                type: array
    //                items:
    //                  "$ref": "#/components/schemas/pull-request-review-comment"
    //              examples:
    //                default:
    //                  "$ref": "#/components/examples/pull-request-review-comment-items"
    //          headers:
    //            Link:
    //              "$ref": "#/components/headers/link"
    //      x-github:
    //        githubCloudOnly: false
    //        enabledForGitHubApps: true
    //        category: pulls
    //        subcategory: comments
    suspend fun getPullRequestComments(input: PullRequestInput): List<Comment> {
        val reviewComments: List<ReviewCommentResponse> = client
            .get("https://api.github.com/repos/${input.owner}/${input.repo}/pulls/${input.number}/comments")
            .body()

        val unifiedReview = reviewComments.map {
            Comment(
                id = it.id,
                author = it.user?.login ?: "",
                body = it.body ?: "",
                createdAt = it.createdAt ?: "",
                type = CommentType.Review
            )
        }
        return unifiedReview.sortedBy { it.createdAt }
    }

    @Description("Get Pull Request Comments Input")
    @Serializable
    class PullRequestInput(
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
        val number: Int? = null,
        val title: String? = null,
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
