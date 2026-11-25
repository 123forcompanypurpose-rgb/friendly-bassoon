"use client";

import Cropper, { type Area } from "react-easy-crop";
import { EditorContent, useEditor } from "@tiptap/react";
import TiptapImage from "@tiptap/extension-image";
import StarterKit from "@tiptap/starter-kit";
import Link from "next/link";
import { useTranslations } from "next-intl";
import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type ChangeEvent,
  type DragEvent,
} from "react";
import {
  ApiError,
  confirmStoryCover,
  createChapter,
  createStory,
  deleteChapter,
  deleteStory,
  getGenres,
  getMyStories,
  getMyStoryChapters,
  getChapterDraft,
  publishChapter,
  publishStory,
  reorderStoryChapters,
  saveChapterDraft,
  scheduleChapter,
  unpublishChapter,
  unpublishStory,
  updateStory,
  presignStoryCover,
  presignChapterImage,
  confirmChapterImage,
  type Chapter,
  type ChapterSummary,
  type Genre,
  type Story,
} from "@/lib/api/client";
import { requireValue, storyHref } from "@/lib/content";

type StoryForm = {
  title: string;
  genreId: string;
  language: string;
  maturity: "EVERYONE" | "TEEN" | "MATURE";
  status: "ONGOING" | "COMPLETED" | "HIATUS";
  blurb: string;
  tags: string;
};

function emptyStoryForm(genreId = ""): StoryForm {
  return {
    title: "",
    genreId,
    language: "en",
    maturity: "EVERYONE",
    status: "ONGOING",
    blurb: "",
    tags: "",
  };
}

function storyToForm(story: Story): StoryForm {
  return {
    title: story.title ?? "",
    genreId: String(story.genre?.id ?? ""),
    language: story.language ?? "en",
    maturity:
      story.maturity === "MATURE" || story.maturity === "TEEN"
        ? story.maturity
        : "EVERYONE",
    status:
      story.status === "COMPLETED" || story.status === "HIATUS"
        ? story.status
        : "ONGOING",
    blurb: story.blurb ?? "",
    tags: (story.tags ?? []).map((tag) => tag.name).join(", "),
  };
}

function errorMessage(error: unknown, fallback: string) {
  return error instanceof ApiError ? error.message : fallback;
}

async function loadAllStories() {
  const firstPage = await getMyStories();
  const stories = [...(firstPage.content ?? [])];
  for (let page = 1; page < (firstPage.totalPages ?? 1); page += 1) {
    const result = await getMyStories(page);
    stories.push(...(result.content ?? []));
  }
  return stories;
}

async function loadAllChapters(storyId: number) {
  const firstPage = await getMyStoryChapters(storyId);
  const chapters = [...(firstPage.content ?? [])];
  for (let page = 1; page < (firstPage.totalPages ?? 1); page += 1) {
    const result = await getMyStoryChapters(storyId, page);
    chapters.push(...(result.content ?? []));
  }
  return chapters;
}

async function cropImage(
  imageUrl: string,
  area: Area,
): Promise<Blob> {
  const image = new Image();
  image.src = imageUrl;
  await new Promise<void>((resolve, reject) => {
    image.onload = () => resolve();
    image.onerror = () => reject(new Error("The selected cover could not be read."));
  });

  const canvas = document.createElement("canvas");
  canvas.width = area.width;
  canvas.height = area.height;
  const context = canvas.getContext("2d");
  if (!context) throw new Error("Cover cropping is not supported in this browser.");
  context.drawImage(
    image,
    area.x,
    area.y,
    area.width,
    area.height,
    0,
    0,
    area.width,
    area.height,
  );
  return new Promise<Blob>((resolve, reject) => {
    canvas.toBlob((blob) => {
      if (blob) resolve(blob);
      else reject(new Error("The cover image could not be cropped."));
    }, "image/jpeg", 0.9);
  });
}

function localDateTimeValue() {
  const date = new Date(Date.now() + 60 * 60 * 1000);
  date.setMinutes(date.getMinutes() - date.getTimezoneOffset());
  return date.toISOString().slice(0, 16);
}

export function StudioWorkspace() {
  const t = useTranslations("Studio");
  const [stories, setStories] = useState<Story[]>([]);
  const [genres, setGenres] = useState<Genre[]>([]);
  const [selectedStory, setSelectedStory] = useState<Story | null>(null);
  const [chapters, setChapters] = useState<ChapterSummary[]>([]);
  const [storyForm, setStoryForm] = useState<StoryForm>(emptyStoryForm());
  const [newChapterTitle, setNewChapterTitle] = useState("");
  const [editingChapter, setEditingChapter] = useState<Chapter | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadFailed, setLoadFailed] = useState(false);
  const [authRequired, setAuthRequired] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [pendingCover, setPendingCover] = useState<Blob | null>(null);
  const [coverPreview, setCoverPreview] = useState("");
  const [coverSource, setCoverSource] = useState("");
  const [crop, setCrop] = useState({ x: 0, y: 0 });
  const [zoom, setZoom] = useState(1);
  const [cropArea, setCropArea] = useState<Area | null>(null);
  const [draggedChapterId, setDraggedChapterId] = useState<number | null>(null);
  const [chapterPreview, setChapterPreview] = useState<Chapter | null>(null);

  const refreshStories = useCallback(async () => {
    setStories(await loadAllStories());
  }, []);

  useEffect(() => {
    let active = true;
    async function load() {
      setLoading(true);
      setError("");
      try {
        const [loadedStories, loadedGenres] = await Promise.all([
          loadAllStories(),
          getGenres(),
        ]);
        if (!active) return;
        setStories(loadedStories);
        setGenres(loadedGenres);
        setStoryForm(emptyStoryForm(String(loadedGenres[0]?.id ?? "")));
      } catch (loadError) {
        if (!active) return;
        if (loadError instanceof ApiError && loadError.status === 401) {
          setAuthRequired(true);
        } else {
          setLoadFailed(true);
          setError(errorMessage(loadError, t("loadError")));
        }
      } finally {
        if (active) setLoading(false);
      }
    }
    void load();
    return () => {
      active = false;
    };
  }, [t]);

  useEffect(() => {
    return () => {
      if (coverSource) URL.revokeObjectURL(coverSource);
      if (coverPreview) URL.revokeObjectURL(coverPreview);
    };
  }, [coverPreview, coverSource]);

  async function selectStory(story: Story) {
    const storyId = requireValue(story.id, "story id");
    setSelectedStory(story);
    setStoryForm(storyToForm(story));
    setEditingChapter(null);
    setChapterPreview(null);
    setError("");
    setNotice("");
    setBusy(true);
    try {
      setChapters(await loadAllChapters(storyId));
    } catch (loadError) {
      setError(errorMessage(loadError, t("chaptersLoadError")));
    } finally {
      setBusy(false);
    }
  }

  function startNewStory() {
    setSelectedStory(null);
    setChapters([]);
    setEditingChapter(null);
    setChapterPreview(null);
    setStoryForm(emptyStoryForm(String(genres[0]?.id ?? "")));
    setPendingCover(null);
    setCoverPreview("");
    setError("");
    setNotice("");
  }

  async function uploadCover(storyId: number) {
    if (!pendingCover) return null;
    if (pendingCover.size > 2 * 1024 * 1024) {
      throw new Error(t("coverTooLarge"));
    }
    const link = await presignStoryCover(storyId, {
      contentType: "image/jpeg",
      contentLength: pendingCover.size,
    });
    const uploadUrl = requireValue(link.uploadUrl, "cover upload URL");
    const objectKey = requireValue(link.objectKey, "cover object key");
    const uploaded = await fetch(uploadUrl, {
      method: "PUT",
      headers: { "Content-Type": "image/jpeg" },
      body: pendingCover,
    });
    if (!uploaded.ok) throw new Error(t("coverUploadError"));
    const result = await confirmStoryCover(storyId, objectKey);
    setPendingCover(null);
    return result.coverUrl ?? null;
  }

  async function saveStory(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const payload = {
        title: storyForm.title.trim(),
        genreId: Number(storyForm.genreId),
        language: storyForm.language.trim() || "en",
        maturity: storyForm.maturity,
        status: storyForm.status,
        blurb: storyForm.blurb.trim(),
        tags: storyForm.tags
          .split(",")
          .map((tag) => tag.trim())
          .filter(Boolean)
      };
      if (payload.tags.length > 10) {
        setError(t("tagsLimit"));
        return;
      }
      const story = selectedStory?.id
        ? await updateStory(selectedStory.id, payload)
        : await createStory(payload);
      const storyId = requireValue(story.id, "story id");
      const creating = !selectedStory;
      setSelectedStory(story);
      setStoryForm(storyToForm(story));
      if (creating) setChapters(await loadAllChapters(storyId));
      const coverUrl = await uploadCover(storyId);
      if (coverUrl) {
        setSelectedStory((current) =>
          current?.id === storyId ? { ...current, coverUrl } : current,
        );
      }
      setNotice(t(creating ? "storyCreated" : "storySaved"));
      await refreshStories();
    } catch (saveError) {
      setError(errorMessage(saveError, t("saveError")));
    } finally {
      setBusy(false);
    }
  }

  async function changeStoryVisibility() {
    if (!selectedStory?.id) return;
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const saved =
        selectedStory.visibility === "PUBLISHED"
          ? await unpublishStory(selectedStory.id)
          : await publishStory(selectedStory.id);
      setSelectedStory(saved);
      setNotice(
        t(saved.visibility === "PUBLISHED" ? "storyPublished" : "storyUnpublished"),
      );
      await refreshStories();
    } catch (visibilityError) {
      setError(errorMessage(visibilityError, t("publishStoryError")));
    } finally {
      setBusy(false);
    }
  }

  async function removeStory() {
    if (!selectedStory?.id || !window.confirm(t("confirmDeleteStory"))) return;
    setBusy(true);
    setError("");
    try {
      await deleteStory(selectedStory.id);
      setSelectedStory(null);
      setChapters([]);
      setStoryForm(emptyStoryForm(String(genres[0]?.id ?? "")));
      setNotice(t("storyDeleted"));
      await refreshStories();
    } catch (deleteError) {
      setError(errorMessage(deleteError, t("deleteError")));
    } finally {
      setBusy(false);
    }
  }

  async function addChapter(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedStory?.id) return;
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const chapter = await createChapter(selectedStory.id, {
        title: newChapterTitle.trim(),
        contentHtml: "<p></p>",
      });
      setEditingChapter(chapter);
      setNewChapterTitle("");
      setNotice(t("chapterCreated"));
      setChapters(await loadAllChapters(selectedStory.id));
    } catch (createError) {
      setError(errorMessage(createError, t("createChapterError")));
    } finally {
      setBusy(false);
    }
  }

  async function editChapter(chapterId: number) {
    setBusy(true);
    setError("");
    setChapterPreview(null);
    try {
      setEditingChapter(await getChapterDraft(chapterId));
    } catch (loadError) {
      setError(errorMessage(loadError, t("chapterLoadError")));
    } finally {
      setBusy(false);
    }
  }

  async function removeChapter(chapterId: number) {
    if (!selectedStory?.id || !window.confirm(t("confirmDeleteChapter"))) return;
    setBusy(true);
    setError("");
    try {
      await deleteChapter(chapterId);
      setChapters(await loadAllChapters(selectedStory.id));
      setNotice(t("chapterDeleted"));
    } catch (deleteError) {
      setError(errorMessage(deleteError, t("deleteError")));
    } finally {
      setBusy(false);
    }
  }

  async function moveChapter(
    chapterId: number,
    direction: -1 | 1,
  ) {
    if (!selectedStory?.id) return;
    const from = chapters.findIndex((chapter) => chapter.id === chapterId);
    const to = from + direction;
    if (from < 0 || to < 0 || to >= chapters.length) return;
    const reordered = [...chapters];
    [reordered[from], reordered[to]] = [reordered[to], reordered[from]];
    await saveChapterOrder(reordered);
  }

  async function retryLoad() {
    setLoading(true);
    setLoadFailed(false);
    setError("");
    try {
      const [loadedStories, loadedGenres] = await Promise.all([
        loadAllStories(),
        getGenres(),
      ]);
      setStories(loadedStories);
      setGenres(loadedGenres);
      if (!storyForm.genreId) {
        setStoryForm(emptyStoryForm(String(loadedGenres[0]?.id ?? "")));
      }
    } catch (loadError) {
      setLoadFailed(true);
      setError(errorMessage(loadError, t("loadError")));
    } finally {
      setLoading(false);
    }
  }

  async function saveChapterOrder(reordered: ChapterSummary[]) {
    if (!selectedStory?.id) return;
    const previous = chapters;
    setChapters(reordered);
    setError("");
    try {
      await reorderStoryChapters(
        selectedStory.id,
        reordered.map((chapter) => requireValue(chapter.id, "chapter id")),
      );
    } catch (reorderError) {
      setChapters(previous);
      setError(errorMessage(reorderError, t("reorderError")));
    }
  }

  function onChapterDrop(
    event: DragEvent<HTMLLIElement>,
    targetId: number,
  ) {
    event.preventDefault();
    if (draggedChapterId === null || draggedChapterId === targetId) return;
    const reordered = [...chapters];
    const sourceIndex = reordered.findIndex(
      (chapter) => chapter.id === draggedChapterId,
    );
    const targetIndex = reordered.findIndex(
      (chapter) => chapter.id === targetId,
    );
    if (sourceIndex < 0 || targetIndex < 0) return;
    const [dragged] = reordered.splice(sourceIndex, 1);
    reordered.splice(targetIndex, 0, dragged);
    setDraggedChapterId(null);
    void saveChapterOrder(reordered);
  }

  async function uploadCroppedCover() {
    if (!coverSource || !cropArea) return;
    setBusy(true);
    setError("");
    try {
      const blob = await cropImage(coverSource, cropArea);
      if (coverPreview) URL.revokeObjectURL(coverPreview);
      setCoverPreview(URL.createObjectURL(blob));
      setPendingCover(blob);
      setCoverSource("");
    } catch (cropError) {
      setError(errorMessage(cropError, t("coverCropError")));
    } finally {
      setBusy(false);
    }
  }

  function chooseCover(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (!file) return;
    if (!["image/jpeg", "image/png", "image/webp"].includes(file.type)) {
      setError(t("coverTypeError"));
      return;
    }
    if (file.size > 2 * 1024 * 1024) {
      setError(t("coverTooLarge"));
      return;
    }
    if (coverSource) URL.revokeObjectURL(coverSource);
    setCoverSource(URL.createObjectURL(file));
    setCrop({ x: 0, y: 0 });
    setZoom(1);
    setError("");
  }

  if (loading) {
    return (
      <main aria-busy="true" className="page-shell studio-page">
        <p className="eyebrow">{t("eyebrow")}</p>
        <div className="skeleton skeleton-title" />
        <div className="skeleton-grid">
          <div className="skeleton skeleton-card" />
          <div className="skeleton skeleton-card" />
        </div>
      </main>
    );
  }

  if (authRequired) {
    return (
      <main className="page-shell studio-page">
        <p className="eyebrow">{t("eyebrow")}</p>
        <section className="glass studio-empty">
          <h1>{t("signInTitle")}</h1>
          <p>{t("signInBody")}</p>
          <Link className="primary-button" href="/login?next=%2Fstudio">
            {t("signIn")}
          </Link>
        </section>
      </main>
    );
  }

  return (
    <main className="page-shell studio-page">
      <div className="section-heading">
        <div>
          <p className="eyebrow">{t("eyebrow")}</p>
          <h1>{t("title")}</h1>
        </div>
        <button className="primary-button" onClick={startNewStory} type="button">
          {t("newStory")}
        </button>
      </div>

      {error && (
        <p className="form-message form-error" role="alert">
          {error}
          {loadFailed && (
            <button className="studio-secondary-button" onClick={() => void retryLoad()} type="button">
              {t("retry")}
            </button>
          )}
        </p>
      )}
      {notice && <p className="form-message form-success" role="status">{notice}</p>}

      {chapterPreview ? (
        <section className="glass studio-panel">
          <div className="section-heading">
            <h2>{t("previewTitle")}</h2>
            <button
              className="studio-secondary-button"
              onClick={() => setChapterPreview(null)}
              type="button"
            >
              {t("backToEditor")}
            </button>
          </div>
          <iframe
            className="studio-preview-frame"
            sandbox=""
            srcDoc={`<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"><style>body{max-width:680px;margin:40px auto;padding:0 20px;font:18px/1.8 Georgia,serif;color:#241a36}img{max-width:100%}</style></head><body>${chapterPreview.contentHtml ?? ""}</body></html>`}
            title={t("previewTitle")}
          />
        </section>
      ) : editingChapter ? (
        <ChapterEditor
          chapter={editingChapter}
          onBack={() => {
            setEditingChapter(null);
            if (selectedStory?.id) {
              void loadAllChapters(selectedStory.id)
                .then(setChapters)
                .catch((loadError: unknown) =>
                  setError(errorMessage(loadError, t("chaptersLoadError"))),
                );
            }
          }}
          onPreview={setChapterPreview}
          t={t}
        />
      ) : (
        <div className="studio-layout">
          <aside aria-label={t("yourStories")} className="glass studio-sidebar">
            <div className="section-heading">
              <h2>{t("yourStories")}</h2>
              <span>{stories.length}</span>
            </div>
            {stories.length === 0 ? (
              <p className="empty-inline">{t("emptyStories")}</p>
            ) : (
              <ul className="studio-story-list">
                {stories.map((story) => (
                  <li key={story.id}>
                    <button
                      aria-current={selectedStory?.id === story.id ? "page" : undefined}
                      className="studio-story-choice"
                      onClick={() => void selectStory(story)}
                      type="button"
                    >
                      <strong>{story.title}</strong>
                      <span>
                        {t("storySummary", {
                          chapters: story.chapterCount ?? 0,
                          words: (story.wordCount ?? 0).toLocaleString(),
                        })}
                      </span>
                      <small>{story.visibility ?? "DRAFT"}</small>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </aside>

          <section aria-labelledby="story-form-title" className="glass studio-panel">
            <div className="section-heading">
              <h2 id="story-form-title">
                {selectedStory ? t("editStory") : t("createStory")}
              </h2>
              {selectedStory?.id && (
                <Link
                  className="studio-secondary-button"
                  href={storyHref(selectedStory)}
                  target="_blank"
                >
                  {t("viewStory")}
                </Link>
              )}
            </div>
            <form className="studio-form" onSubmit={(event) => void saveStory(event)}>
              <label>
                {t("storyTitle")}
                <input
                  maxLength={120}
                  minLength={3}
                  onChange={(event) =>
                    setStoryForm({ ...storyForm, title: event.target.value })
                  }
                  required
                  value={storyForm.title}
                />
              </label>
              <div className="studio-form-row">
                <label>
                  {t("genre")}
                  <select
                    onChange={(event) =>
                      setStoryForm({ ...storyForm, genreId: event.target.value })
                    }
                    required
                    value={storyForm.genreId}
                  >
                    <option value="">{t("chooseGenre")}</option>
                    {genres.map((genre) => (
                      <option key={genre.id} value={genre.id}>
                        {genre.name}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  {t("language")}
                  <input
                    maxLength={10}
                    onChange={(event) =>
                      setStoryForm({ ...storyForm, language: event.target.value })
                    }
                    value={storyForm.language}
                  />
                </label>
              </div>
              <div className="studio-form-row">
                <label>
                  {t("maturity")}
                  <select
                    onChange={(event) =>
                      setStoryForm({
                        ...storyForm,
                        maturity: event.target.value as StoryForm["maturity"],
                      })
                    }
                    value={storyForm.maturity}
                  >
                    <option value="EVERYONE">{t("everyone")}</option>
                    <option value="TEEN">{t("teen")}</option>
                    <option value="MATURE">{t("mature")}</option>
                  </select>
                </label>
                <label>
                  {t("storyStatus")}
                  <select
                    onChange={(event) =>
                      setStoryForm({
                        ...storyForm,
                        status: event.target.value as StoryForm["status"],
                      })
                    }
                    value={storyForm.status}
                  >
                    <option value="ONGOING">{t("ongoing")}</option>
                    <option value="COMPLETED">{t("completed")}</option>
                    <option value="HIATUS">{t("hiatus")}</option>
                  </select>
                </label>
              </div>
              <label>
                {t("blurb")}
                <textarea
                  maxLength={2000}
                  onChange={(event) =>
                    setStoryForm({ ...storyForm, blurb: event.target.value })
                  }
                  rows={5}
                  value={storyForm.blurb}
                />
              </label>
              <label>
                {t("tags")}
                <input
                  onChange={(event) =>
                    setStoryForm({ ...storyForm, tags: event.target.value })
                  }
                  placeholder={t("tagsPlaceholder")}
                  value={storyForm.tags}
                />
              </label>
              <label>
                {t("cover")}
                <input
                  accept="image/jpeg,image/png,image/webp"
                  onChange={chooseCover}
                  type="file"
                />
              </label>
              {(coverPreview || selectedStory?.coverUrl) && (
                // eslint-disable-next-line @next/next/no-img-element
                <img
                  alt={t("coverPreview")}
                  className="studio-cover-preview"
                  src={coverPreview || selectedStory?.coverUrl}
                />
              )}
              {pendingCover && <p className="studio-hint">{t("coverReady")}</p>}
              <div className="studio-action-row">
                <button className="primary-button" disabled={busy} type="submit">
                  {busy ? t("saving") : t("saveStory")}
                </button>
                {selectedStory?.id && (
                  <>
                    <button
                      className="studio-secondary-button"
                      disabled={busy}
                      onClick={() => void changeStoryVisibility()}
                      type="button"
                    >
                      {selectedStory.visibility === "PUBLISHED"
                        ? t("unpublishStory")
                        : t("publishStory")}
                    </button>
                    <button
                      className="studio-danger-button"
                      disabled={busy}
                      onClick={() => void removeStory()}
                      type="button"
                    >
                      {t("deleteStory")}
                    </button>
                  </>
                )}
              </div>
            </form>

            {selectedStory?.id && (
              <section aria-labelledby="chapters-title" className="studio-chapters">
                <div className="section-heading">
                  <h2 id="chapters-title">{t("chapters")}</h2>
                  <span>{chapters.length}</span>
                </div>
                <form className="studio-new-chapter" onSubmit={(event) => void addChapter(event)}>
                  <label>
                    {t("newChapterTitle")}
                    <input
                      maxLength={150}
                      onChange={(event) => setNewChapterTitle(event.target.value)}
                      required
                      value={newChapterTitle}
                    />
                  </label>
                  <button className="studio-secondary-button" disabled={busy} type="submit">
                    {t("addChapter")}
                  </button>
                </form>
                {chapters.length === 0 ? (
                  <p className="empty-state glass">{t("emptyChapters")}</p>
                ) : (
                  <ol className="studio-chapter-list">
                    {chapters.map((chapter, index) => {
                      const chapterId = requireValue(chapter.id, "chapter id");
                      return (
                        <li
                          draggable
                          key={chapterId}
                          onDragOver={(event) => event.preventDefault()}
                          onDragStart={() => setDraggedChapterId(chapterId)}
                          onDrop={(event) => onChapterDrop(event, chapterId)}
                        >
                          <span className="studio-drag-handle" aria-hidden="true">⠿</span>
                          <span className="studio-chapter-number">
                            {String(index + 1).padStart(2, "0")}
                          </span>
                          <div className="studio-chapter-info">
                            <strong>{chapter.title}</strong>
                            <small>
                              {chapter.status} · {chapter.wordCount ?? 0} {t("words")}
                            </small>
                          </div>
                          <div className="studio-chapter-actions">
                            <button
                              aria-label={t("moveChapterUp", { title: chapter.title ?? "" })}
                              className="studio-icon-button"
                              disabled={busy || index === 0}
                              onClick={() => void moveChapter(chapterId, -1)}
                              type="button"
                            >
                              ↑
                            </button>
                            <button
                              aria-label={t("moveChapterDown", { title: chapter.title ?? "" })}
                              className="studio-icon-button"
                              disabled={busy || index === chapters.length - 1}
                              onClick={() => void moveChapter(chapterId, 1)}
                              type="button"
                            >
                              ↓
                            </button>
                            <button
                              className="studio-secondary-button"
                              disabled={busy}
                              onClick={() => void editChapter(chapterId)}
                              type="button"
                            >
                              {t("editChapter")}
                            </button>
                            <button
                              aria-label={t("deleteChapter")}
                              className="studio-danger-button"
                              disabled={busy}
                              onClick={() => void removeChapter(chapterId)}
                              type="button"
                            >
                              {t("delete")}
                            </button>
                          </div>
                        </li>
                      );
                    })}
                  </ol>
                )}
              </section>
            )}
          </section>
        </div>
      )}

      {coverSource && (
        <div aria-label={t("cropCover")} aria-modal="true" className="studio-modal" role="dialog">
          <div className="studio-crop-dialog">
            <div className="section-heading">
              <h2>{t("cropCover")}</h2>
              <button
                aria-label={t("cancel")}
                className="studio-icon-button"
                onClick={() => setCoverSource("")}
                type="button"
              >
                ×
              </button>
            </div>
            <div className="studio-crop-area">
              <Cropper
                aspect={2 / 3}
                crop={crop}
                image={coverSource}
                onCropChange={setCrop}
                onCropComplete={(_area, pixels) => setCropArea(pixels)}
                onZoomChange={setZoom}
                zoom={zoom}
              />
            </div>
            <label>
              {t("zoom")}
              <input
                max={3}
                min={1}
                onChange={(event) => setZoom(Number(event.target.value))}
                step={0.1}
                type="range"
                value={zoom}
              />
            </label>
            <button
              className="primary-button"
              disabled={busy || !cropArea}
              onClick={() => void uploadCroppedCover()}
              type="button"
            >
              {t("useCover")}
            </button>
          </div>
        </div>
      )}
    </main>
  );
}

function ChapterEditor({
  chapter,
  onBack,
  onPreview,
  t,
}: {
  chapter: Chapter;
  onBack: () => void;
  onPreview: (chapter: Chapter) => void;
  t: ReturnType<typeof useTranslations<"Studio">>;
}) {
  const initialTitle = chapter.title ?? "";
  const initialNote = chapter.authorNote ?? "";
  const version = useRef(chapter.version ?? 0);
  const titleRef = useRef(initialTitle);
  const noteRef = useRef(initialNote);
  const dirty = useRef(false);
  const saving = useRef(false);
  const conflicted = useRef(false);
  const [title, setTitle] = useState(initialTitle);
  const [note, setNote] = useState(initialNote);
  const [saveState, setSaveState] = useState<"saved" | "unsaved" | "saving" | "error" | "conflict">("saved");
  const [error, setError] = useState("");
  const [scheduleAt, setScheduleAt] = useState(localDateTimeValue);
  const [status, setStatus] = useState(chapter.status ?? "DRAFT");
  const [imageUploading, setImageUploading] = useState(false);
  const imageInputRef = useRef<HTMLInputElement>(null);
  const editor = useEditor({
    extensions: [
      StarterKit.configure({ link: { openOnClick: false } }),
      TiptapImage.configure({ allowBase64: false }),
    ],
    content: chapter.contentHtml || "<p></p>",
    immediatelyRender: false,
    onUpdate: () => {
      dirty.current = true;
      setSaveState("unsaved");
    },
  });

  const saveDraft = useCallback(async () => {
    if (!editor || saving.current || conflicted.current) return null;
    if (!dirty.current) {
      return {
        ...chapter,
        version: version.current,
        title: titleRef.current,
        authorNote: noteRef.current,
        contentHtml: editor.getHTML(),
      };
    }
    saving.current = true;
    setSaveState("saving");
    setError("");
    const snapshot = {
      title: titleRef.current.trim(),
      authorNote: noteRef.current,
      contentHtml: editor.getHTML(),
    };
    try {
      const saved = await saveChapterDraft(
        requireValue(chapter.id, "chapter id"),
        {
          expectedVersion: version.current,
          ...snapshot,
        },
      );
      version.current = requireValue(saved.version, "chapter version");
      const unchanged =
        snapshot.title === titleRef.current.trim() &&
        snapshot.authorNote === noteRef.current &&
        snapshot.contentHtml === editor.getHTML();
      dirty.current = !unchanged;
      setSaveState(unchanged ? "saved" : "unsaved");
      return saved;
    } catch (saveError) {
      if (saveError instanceof ApiError && saveError.status === 409) {
        conflicted.current = true;
        setSaveState("conflict");
        setError(t("versionConflict"));
      } else {
        setSaveState("error");
        setError(errorMessage(saveError, t("saveError")));
      }
      return null;
    } finally {
      saving.current = false;
    }
  }, [chapter, editor, t]);

  useEffect(() => {
    const timer = window.setInterval(() => {
      if (dirty.current) void saveDraft();
    }, 10_000);
    return () => window.clearInterval(timer);
  }, [saveDraft]);

  useEffect(() => {
    function beforeUnload(event: BeforeUnloadEvent) {
      if (!dirty.current) return;
      event.preventDefault();
      event.returnValue = "";
    }
    window.addEventListener("beforeunload", beforeUnload);
    return () => window.removeEventListener("beforeunload", beforeUnload);
  }, []);

  function updateTitle(value: string) {
    titleRef.current = value;
    setTitle(value);
    dirty.current = true;
    setSaveState("unsaved");
  }

  function updateNote(value: string) {
    noteRef.current = value;
    setNote(value);
    dirty.current = true;
    setSaveState("unsaved");
  }

  async function reloadDraft() {
    setError("");
    try {
      const fresh = await getChapterDraft(requireValue(chapter.id, "chapter id"));
      version.current = requireValue(fresh.version, "chapter version");
      titleRef.current = fresh.title ?? "";
      noteRef.current = fresh.authorNote ?? "";
      setTitle(titleRef.current);
      setNote(noteRef.current);
      editor?.commands.setContent(fresh.contentHtml ?? "<p></p>");
      setStatus(fresh.status ?? "DRAFT");
      dirty.current = false;
      conflicted.current = false;
      setSaveState("saved");
    } catch (reloadError) {
      setError(errorMessage(reloadError, t("chapterLoadError")));
    }
  }

  async function backToStory() {
    if (conflicted.current) {
      if (window.confirm(t("discardConflictChanges"))) onBack();
      return;
    }
    if (dirty.current && !(await saveDraft())) return;
    onBack();
  }

  async function waitForSaveToFinish() {
    for (let attempt = 0; attempt < 40 && saving.current; attempt += 1) {
      await new Promise((resolve) => window.setTimeout(resolve, 50));
    }
  }

  async function publish() {
    await waitForSaveToFinish();
    const saved = await saveDraft();
    if (!saved) return;
    setError("");
    try {
      const result = await publishChapter(
        requireValue(chapter.id, "chapter id"),
        requireValue(saved.version, "chapter version"),
      );
      version.current = requireValue(result.version, "chapter version");
      dirty.current = false;
      setStatus(result.status ?? "PUBLISHED");
      setSaveState("saved");
    } catch (publishError) {
      setError(errorMessage(publishError, t("publishChapterError")));
    }
  }

  async function schedule() {
    await waitForSaveToFinish();
    const saved = await saveDraft();
    if (!saved) return;
    setError("");
    try {
      const result = await scheduleChapter(
        requireValue(chapter.id, "chapter id"),
        {
          expectedVersion: requireValue(saved.version, "chapter version"),
          publishAt: new Date(scheduleAt).toISOString(),
        },
      );
      version.current = requireValue(result.version, "chapter version");
      dirty.current = false;
      setStatus(result.status ?? "SCHEDULED");
      setSaveState("saved");
    } catch (scheduleError) {
      setError(errorMessage(scheduleError, t("scheduleError")));
    }
  }

  async function unpublish() {
    setError("");
    try {
      const result = await unpublishChapter(requireValue(chapter.id, "chapter id"));
      version.current = requireValue(result.version, "chapter version");
      setStatus(result.status ?? "DRAFT");
    } catch (unpublishError) {
      setError(errorMessage(unpublishError, t("unpublishChapterError")));
    }
  }

  function insertLink() {
    const href = window.prompt(t("linkPrompt"));
    if (!href || !editor) return;
    editor.chain().focus().extendMarkRange("link").setLink({ href }).run();
  }

  async function uploadChapterImage(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file || !editor) return;
    if (file.size > 5 * 1024 * 1024) {
      setError(t("imageTooLarge"));
      return;
    }
    if (!["image/jpeg", "image/png", "image/webp"].includes(file.type)) {
      setError(t("imageTypeError"));
      return;
    }

    setImageUploading(true);
    setError("");
    try {
      const link = await presignChapterImage(requireValue(chapter.id, "chapter id"), {
        contentType: file.type as "image/jpeg" | "image/png" | "image/webp",
        contentLength: file.size,
      });
      const uploaded = await fetch(requireValue(link.uploadUrl, "image upload URL"), {
        method: "PUT",
        headers: { "Content-Type": file.type },
        body: file,
      });
      if (!uploaded.ok) throw new Error(t("imageUploadError"));
      const result = await confirmChapterImage(
        requireValue(chapter.id, "chapter id"),
        requireValue(link.objectKey, "image object key"),
      );
      const imageUrl = requireValue(result.imageUrl, "image URL");
      editor.chain().focus().setImage({ src: imageUrl, alt: file.name }).run();
      dirty.current = true;
      setSaveState("unsaved");
    } catch (uploadError) {
      setError(errorMessage(uploadError, t("imageUploadError")));
    } finally {
      setImageUploading(false);
    }
  }

  const wordCount = editor
    ? editor.getText().trim().split(/\s+/).filter(Boolean).length
    : chapter.wordCount ?? 0;
  const toolbar = [
    { key: "bold", label: t("bold"), run: () => editor?.chain().focus().toggleBold().run(), active: editor?.isActive("bold") },
    { key: "italic", label: t("italic"), run: () => editor?.chain().focus().toggleItalic().run(), active: editor?.isActive("italic") },
    { key: "underline", label: t("underline"), run: () => editor?.chain().focus().toggleUnderline().run(), active: editor?.isActive("underline") },
    { key: "heading", label: t("heading"), run: () => editor?.chain().focus().toggleHeading({ level: 2 }).run(), active: editor?.isActive("heading", { level: 2 }) },
    { key: "quote", label: t("blockquote"), run: () => editor?.chain().focus().toggleBlockquote().run(), active: editor?.isActive("blockquote") },
    { key: "list", label: t("bulletList"), run: () => editor?.chain().focus().toggleBulletList().run(), active: editor?.isActive("bulletList") },
    { key: "link", label: t("link"), run: insertLink, active: editor?.isActive("link") },
    { key: "image", label: t("image"), run: () => imageInputRef.current?.click(), active: false },
  ];

  return (
    <section aria-labelledby="chapter-editor-title" className="glass studio-panel">
      <div className="section-heading">
        <div>
          <p className="eyebrow">{t("chapterEditor")}</p>
          <h2 id="chapter-editor-title">{t("chapterNumber", { number: chapter.number ?? 0 })}</h2>
        </div>
        <button className="studio-secondary-button" onClick={() => void backToStory()} type="button">
          {t("backToStory")}
        </button>
      </div>
      <label className="studio-field">
        {t("chapterTitle")}
        <input
          maxLength={150}
          onBlur={() => void saveDraft()}
          onChange={(event) => updateTitle(event.target.value)}
          required
          value={title}
        />
      </label>
      <input
        accept="image/jpeg,image/png,image/webp"
        className="studio-visually-hidden"
        onChange={(event) => void uploadChapterImage(event)}
        ref={imageInputRef}
        type="file"
      />
      <div aria-label={t("formatting")} className="studio-toolbar" role="toolbar">
        {toolbar.map((item) => (
          <button
            aria-pressed={Boolean(item.active)}
            className={item.active ? "studio-format-button active" : "studio-format-button"}
            disabled={!editor || (item.key === "image" && imageUploading)}
            key={item.key}
            onClick={item.run}
            type="button"
          >
            {item.label}
          </button>
        ))}
      </div>
      <div className="studio-editor" onBlur={() => void saveDraft()}>
        <EditorContent editor={editor} />
      </div>
      <div className="studio-editor-meta">
        <span>{t("wordCount", { count: wordCount })}</span>
        <span aria-live="polite">
          {saveState === "saved" && t("saved")}
          {saveState === "unsaved" && t("unsaved")}
          {saveState === "saving" && t("saving")}
          {saveState === "error" && t("saveFailed")}
          {saveState === "conflict" && t("conflict")}
        </span>
      </div>
      <label className="studio-field">
        {t("authorNote")}
        <textarea
          maxLength={2000}
          onBlur={() => void saveDraft()}
          onChange={(event) => updateNote(event.target.value)}
          rows={3}
          value={note}
        />
      </label>
      {error && <p className="form-message form-error" role="alert">{error}</p>}
      {saveState === "conflict" && (
        <button className="studio-secondary-button" onClick={() => void reloadDraft()} type="button">
          {t("reloadDraft")}
        </button>
      )}
      <div className="studio-editor-actions">
        <button
          className="studio-secondary-button"
          disabled={!editor}
          onClick={() =>
            onPreview({
              ...chapter,
              title,
              authorNote: note,
              contentHtml: editor?.getHTML() ?? "",
            })
          }
          type="button"
        >
          {t("preview")}
        </button>
        <button
          className="studio-secondary-button"
          disabled={!dirty.current || saveState === "saving"}
          onClick={() => void saveDraft()}
          type="button"
        >
          {t("saveDraft")}
        </button>
        {status === "PUBLISHED" ? (
          <button className="studio-secondary-button" onClick={() => void unpublish()} type="button">
            {t("unpublishChapter")}
          </button>
        ) : (
          <>
            <label className="studio-schedule-field">
              {t("scheduleAt")}
              <input
                min={localDateTimeValue()}
                onChange={(event) => setScheduleAt(event.target.value)}
                type="datetime-local"
                value={scheduleAt}
              />
            </label>
            <button className="studio-secondary-button" onClick={() => void schedule()} type="button">
              {t("schedule")}
            </button>
            <button className="primary-button" onClick={() => void publish()} type="button">
              {t("publishChapter")}
            </button>
          </>
        )}
      </div>
    </section>
  );
}
