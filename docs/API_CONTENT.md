# Content API (Phase 2)

All routes are under `/api/v1`. Authenticated requests use the existing
`access_token` cookie and must include `X-Requested-With: novelverse` on
mutations. Authoring requires an active account with a verified email. Content
that a caller cannot access is returned as `404 CONTENT_NOT_FOUND`.

## Catalog

| Method and route | Access | Purpose |
|---|---|---|
| `GET /genres` | Public | List active, ordered genres |
| `GET /tags?q=...` | Public | Suggest up to 20 tags, ordered by use |

Tags are created or reused when a story is saved. Each story can have at most
10 tags.

## Stories

| Method and route | Access | Purpose |
|---|---|---|
| `GET /stories?genre=...&page=0&size=20` | Public | Browse published stories; mature stories are excluded unless the signed-in user is 18+ and enabled mature content |
| `GET /stories/{storyId}` | Public/owner/moderator | Read a published or unlisted story; owners can also read drafts |
| `GET /me/stories?page=0&size=20` | Verified author | List owned stories, including drafts |
| `POST /stories` | Verified author | Create a draft |
| `PUT /stories/{storyId}` | Owner | Update metadata and tags |
| `DELETE /stories/{storyId}` | Owner | Soft-delete a story |
| `POST /stories/{storyId}/publish` | Owner | Publish after at least one chapter is published |
| `POST /stories/{storyId}/unlist` | Owner | Keep direct access while hiding from browse |
| `POST /stories/{storyId}/unpublish` | Owner | Return to draft visibility |

Slugs are derived from titles and unique. The numeric story ID is authoritative,
so changing a title does not invalidate ID-based links. Browse results include
only `PUBLISHED` stories; `UNLISTED` stories are available by direct ID.

All list responses use `{ content, page, size, totalElements, totalPages }`.
The maximum page size is 50.

## Story cover uploads

1. `POST /stories/{storyId}/cover/presign` with `{ "contentType": "image/png", "contentLength": 1234 }`.
2. PUT the bytes to the returned `uploadUrl` using the same content type.
3. `POST /stories/{storyId}/cover/confirm` with the returned `objectKey`.

Only JPEG, PNG, and WebP files up to 2 MiB are accepted. The API checks actual
image format and dimensions, resizes the image to at most 800 pixels on either
side, converts it to WebP, and updates the story. Uploads require S3-compatible
storage and a public `S3_PUBLIC_BASE_URL`; otherwise the upload API returns
`503 COVER_STORAGE_UNAVAILABLE`.

## Chapters

| Method and route | Access | Purpose |
|---|---|---|
| `GET /stories/{storyId}/chapters?page=0&size=50` | Public/owner | List published chapters, or all active chapters for the owner |
| `POST /stories/{storyId}/chapters` | Owner | Create a chapter and initial draft |
| `GET /chapters/{chapterId}` | Public/owner/moderator | Read a published chapter, owned draft, or moderation-visible content |
| `GET /chapters/{chapterId}/draft` | Owner | Read the working draft |
| `PUT /chapters/{chapterId}/draft` | Owner | Autosave title, HTML, and note |
| `POST /chapters/{chapterId}/publish` | Owner | Publish the latest saved draft |
| `POST /chapters/{chapterId}/schedule` | Owner | Schedule an unpublished draft for a future time |
| `POST /chapters/{chapterId}/unpublish` | Owner | Return a published chapter to draft |
| `DELETE /chapters/{chapterId}` | Owner | Soft-delete a chapter |
| `PUT /stories/{storyId}/chapters/order` | Owner | Replace the order of every active chapter |

Chapter write responses include `version`. Draft saves and publish requests
must send that value as `expectedVersion`; stale requests return
`409 CHAPTER_VERSION_CONFLICT`. Reordering requires every active chapter ID
exactly once and runs in a transaction using the schema's deferred unique
constraint. Published chapter responses include previous/next IDs and
estimated reading time.

Chapter HTML is allow-list sanitized before storage. Only configured storage
URLs are retained for inline images; content is limited to 50,000 words.
Published chapter and story word/chapter counters are updated in the same
transaction.

Scheduled drafts are published by a once-per-minute job guarded by ShedLock.
Migration `V6__scheduled_task_locks.sql` creates the shared lock table. Mature
stories and chapters require an eligible 18+ account with mature content enabled.
