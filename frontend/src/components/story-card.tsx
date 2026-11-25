import Link from "next/link";
import { useTranslations } from "next-intl";
import type { Story } from "@/lib/api/client";
import { requireValue, storyHref } from "@/lib/content";

export function StoryCard({ story }: { story: Story }) {
  const t = useTranslations("Story");
  const title = requireValue(story.title, "story title");
  const author = requireValue(story.author, "story author");
  const authorName = requireValue(
    author.displayName ?? author.username,
    "author display name",
  );

  return (
    <article className="story-card glass">
      <Link aria-label={title} className="story-cover" href={storyHref(story)}>
        {story.coverUrl ? (
          // Story covers are uploaded through the backend's validated image flow.
          // eslint-disable-next-line @next/next/no-img-element
          <img alt="" loading="lazy" src={story.coverUrl} />
        ) : (
          <span aria-hidden="true" className="cover-placeholder">
            {title.slice(0, 1)}
          </span>
        )}
      </Link>
      <div className="story-card-copy">
        <p className="story-card-genre">
          {requireValue(story.genre?.name, "story genre")}
        </p>
        <h3>
          <Link href={storyHref(story)}>{title}</Link>
        </h3>
        <p className="story-card-author">
          {t("by", { author: authorName })}
        </p>
        <div className="story-card-meta">
          <span>{story.chapterCount ?? 0} {t("chapters").toLowerCase()}</span>
          <span>{story.ratingCount ? `★ ${story.rating?.toFixed(1)}` : "✦"}</span>
        </div>
      </div>
    </article>
  );
}

export function StoryShelf({
  title,
  stories,
  empty,
}: {
  title: string;
  stories: Story[];
  empty?: string;
}) {
  return (
    <section aria-label={title} className="story-shelf">
      <div className="section-heading">
        <h2>{title}</h2>
      </div>
      {stories.length ? (
        <div className="story-grid">
          {stories.map((story) => (
            <StoryCard key={requireValue(story.id, "story id")} story={story} />
          ))}
        </div>
      ) : (
        empty && <p className="empty-state glass">{empty}</p>
      )}
    </section>
  );
}
