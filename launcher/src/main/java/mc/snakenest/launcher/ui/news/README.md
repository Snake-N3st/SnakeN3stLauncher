# ui.news

The "Actualites" section.

- **`NewsListPage`** / **`NewsListViewModel`** — clickable list of posts
  (title + description), each opening `NewsDetailPage`.
- **`NewsDetailPage`** — full post: title, HTML content rendered in a
  `JEditorPane` (Swing's basic HTML support - Azuriom stores post content as
  rich HTML), and a "Voir sur le site" button that opens `post.url()` in the
  system browser, matching the first mockup's layout.
