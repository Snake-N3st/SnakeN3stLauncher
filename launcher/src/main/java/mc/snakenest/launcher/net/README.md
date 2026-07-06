# net

Low-level HTTP/JSON plumbing. No knowledge of this project's specific API
endpoints lives here - that's `auth`/`modpack`/`news`, which each build on
top of this package.

- **`HttpJsonClient`** — wraps the JDK's built-in `java.net.http.HttpClient`
  (no extra HTTP library needed) with one shared, consistently-configured
  `Gson`. `get`/`postJson` return a `JsonResponse`; `getRaw` returns a
  `RawResponse` for streaming (non-JSON) downloads like modpack blobs or the
  launcher release jar.

- **`JsonResponse`** — status code + raw body, parsed into a type on demand
  via `as(Class)`. Deliberately untyped up front: several endpoints in this
  API return a different JSON shape depending on the status code (e.g. the
  challenge poll returns the key on `200` but `{"message": ...}` on
  `403`/`404`), so the caller decides what to parse based on the status it
  got, rather than this package guessing one shape per endpoint.

- **`RawResponse`** — status code + `InputStream`, `Closeable`. Used where
  the caller wants to hash/write bytes as they stream in (see
  `modpack.ModpackFileDownloader`) instead of buffering the whole response.

**Hard rule**: nothing in this package ever logs a full URI, only
`URI.getPath()`. This API's query strings carry `signature`/`publicKey`/
`client_id`; logging the full URL would print exactly the values a stolen
log file could be replayed with.
