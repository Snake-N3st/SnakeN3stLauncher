# news

Client for Azuriom **core**'s public news feed - not a custom plugin, and
not authenticated (no `client_id`/signature needed, it's public site
content anyone can already read on the website).

- **`Post`** — mirrors `app/Http/Resources/PostResource.php` in Azuriom core
  exactly (checked against the source on the local dev instance, not just
  assumed): `id`, `title`, `description`, `slug`, `url`, `content`,
  `author` (`id`, `name`), `published_at`, `image`. The list endpoint
  (`GET /api/posts`) omits `content`'s heavier sibling `comments`; this
  class doesn't model comments at all since the News page has no comment
  feature planned.

- **`NewsApiClient`** — `listPosts()` (`GET /api/posts`, a bare JSON array)
  and `getPost(id)` (`GET /api/posts/{id}`).

One thing to keep in mind if this ever gets revisited: `published_at` and
`total_size`-style fields elsewhere in this project's own plugins use
snake_case, while `launcher-auth`'s challenge/player endpoints use
camelCase - the API isn't internally consistent about this, so each DTO in
this codebase spells out `@SerializedName` explicitly rather than relying
on one global naming policy.
