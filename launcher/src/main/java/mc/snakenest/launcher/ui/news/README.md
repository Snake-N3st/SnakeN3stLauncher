# ui.news

The Actualités section presents the newest dated post first in a featured card,
followed by the remaining posts. Cards display the site's cover image, plain-text
title and excerpt, French publication date, author and a browser link.

- `NewsListPage` / `NewsListViewModel`: responsive, keyboard-accessible article
  buttons. The featured cover stacks above its text in narrow windows. Images
  load outside the EDT with a bounded in-memory cache and a neutral branded
  fallback when absent or unavailable. No article HTML is executed or fetched by
  the text renderer.
- `LauncherApp.loadNewsList`: routes article selection directly to the system
  browser using `post.url()`. Errors offer retry and the offline action.
- `NewsSectionPage`: retains its list replacement/reset API for loading and error
  states. `NewsDetailPage` remains available to existing preview callers; the
  production feed opens the site directly.
