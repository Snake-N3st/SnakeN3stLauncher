package mc.snakenest.launcher.ui.news;

import mc.snakenest.launcher.news.Post;

import java.util.List;
import java.util.function.Consumer;

/** Data + callbacks for {@link NewsListPage}. Holds no logic of its own. */
public final class NewsListViewModel {

    private final List<Post> posts;
    private final Consumer<Post> onSelect;

    public NewsListViewModel(List<Post> posts, Consumer<Post> onSelect) {
        this.posts = posts;
        this.onSelect = onSelect;
    }

    public List<Post> posts() {
        return posts;
    }

    public void select(Post post) {
        onSelect.accept(post);
    }
}
