const blogMarket = "/assets/blog-market.jpg";

export type BlogSection = {
  heading?: string;
  paragraphs?: string[];
  bullets?: string[];
};

export type BlogPost = {
  slug: string;
  title: string;
  date: string;
  tag: string;
  author: string;
  readMinutes: number;
  excerpt: string;
  img: string;
  body: string[];
  sections: BlogSection[];
};

export const POSTS: BlogPost[] = [];

export function parseMarkdownToSections(markdown: string): BlogSection[] {
  if (!markdown || !markdown.trim()) return [];

  const lines = markdown.split(/\r?\n/);
  const sections: BlogSection[] = [];
  let currentSection: BlogSection = { paragraphs: [], bullets: [] };
  let currentParagraphLines: string[] = [];

  function flushParagraph() {
    if (currentParagraphLines.length > 0) {
      const p = currentParagraphLines.join(" ").trim();
      if (p) {
        if (!currentSection.paragraphs) currentSection.paragraphs = [];
        currentSection.paragraphs.push(p);
      }
      currentParagraphLines = [];
    }
  }

  for (const rawLine of lines) {
    const line = rawLine.trim();

    // Check for headings (e.g., "### Heading", "## Heading", "# Heading")
    const headingMatch = line.match(/^#{1,4}\s+(.+)$/);
    if (headingMatch) {
      flushParagraph();
      if (
        currentSection.heading ||
        (currentSection.paragraphs && currentSection.paragraphs.length > 0) ||
        (currentSection.bullets && currentSection.bullets.length > 0)
      ) {
        sections.push(currentSection);
      }
      currentSection = { heading: headingMatch[1].trim(), paragraphs: [], bullets: [] };
      continue;
    }

    // Check for bullet points (e.g., "- bullet", "* bullet", "• bullet")
    const bulletMatch = line.match(/^[-*•]\s+(.+)$/);
    if (bulletMatch) {
      flushParagraph();
      if (!currentSection.bullets) currentSection.bullets = [];
      currentSection.bullets.push(bulletMatch[1].trim());
      continue;
    }

    // Empty line -> paragraph separator
    if (!line) {
      flushParagraph();
      continue;
    }

    // Regular text line
    currentParagraphLines.push(line);
  }

  flushParagraph();
  if (
    currentSection.heading ||
    (currentSection.paragraphs && currentSection.paragraphs.length > 0) ||
    (currentSection.bullets && currentSection.bullets.length > 0)
  ) {
    sections.push(currentSection);
  }

  return sections.length > 0 ? sections : [{ paragraphs: [markdown.trim()] }];
}

export function formatSectionsToMarkdown(sections: BlogSection[]): string {
  if (!sections || sections.length === 0) return "";
  const parts: string[] = [];

  for (const s of sections) {
    if (s.heading) {
      parts.push(`### ${s.heading}`);
    }
    if (s.paragraphs && s.paragraphs.length > 0) {
      parts.push(s.paragraphs.join("\n\n"));
    }
    if (s.bullets && s.bullets.length > 0) {
      parts.push(s.bullets.map(b => `- ${b}`).join("\n"));
    }
  }

  return parts.join("\n\n");
}

function slugify(s: string) {
  return s.toLowerCase().trim().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
}

function formatDate(d: string): string {
  if (!d) return "2026";
  if (!d.includes("T") && !d.includes("-")) return d;
  try {
    const dateObj = new Date(d);
    if (isNaN(dateObj.getTime())) return d;
    return dateObj.toLocaleDateString("en-US", { month: "short", year: "numeric" });
  } catch {
    return d;
  }
}

export function getAllPublishedPosts(): BlogPost[] {
  let adminPosts: any[] = [];
  try {
    const raw = typeof window !== "undefined" ? localStorage.getItem("nigson.admin.v2") : null;
    if (raw) {
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed.posts)) {
        adminPosts = parsed.posts;
      }
    }
  } catch {
    /* ignore */
  }

  if (adminPosts.length === 0) {
    return POSTS;
  }

  const publishedAdminPosts = adminPosts.filter((p) => p.status === "Published");

  const results: BlogPost[] = [];
  const processedSlugs = new Set<string>();

  for (const ap of publishedAdminPosts) {
    const slug = ap.slug || slugify(ap.title);
    if (!slug || processedSlugs.has(slug)) continue;
    processedSlugs.add(slug);

    const existing = POSTS.find((p) => p.slug === slug || p.slug === ap.slug);

    // Parse sections with full headings and bullets support
    let sections: BlogSection[] = [];
    if (Array.isArray(ap.sections) && ap.sections.length > 0) {
      sections = ap.sections;
    } else if (typeof ap.body === "string" && ap.body.trim()) {
      const parsed = parseMarkdownToSections(ap.body);
      const hasStructure = parsed.some(s => s.heading || (s.bullets && s.bullets.length > 0));
      sections = hasStructure ? parsed : (existing?.sections || parsed);
    } else if (existing?.sections && existing.sections.length > 0) {
      sections = existing.sections;
    } else {
      sections = [{ paragraphs: [ap.excerpt || "No content available."] }];
    }

    // Extract flat body paragraphs for fallback
    let bodyParas: string[] = [];
    if (typeof ap.body === "string" && ap.body.trim()) {
      bodyParas = ap.body.split(/\n\n+/).map((s: string) => s.trim().replace(/^#{1,4}\s+/, "")).filter(Boolean);
    } else if (Array.isArray(ap.body) && ap.body.length > 0) {
      bodyParas = ap.body;
    } else if (existing?.body && existing.body.length > 0) {
      bodyParas = existing.body;
    } else {
      bodyParas = [ap.excerpt || "No content available."];
    }

    results.push({
      slug,
      title: ap.title || existing?.title || "Untitled Article",
      date: ap.date ? formatDate(ap.date) : (existing?.date || "2026"),
      tag: ap.tag || existing?.tag || "Industry",
      author: ap.author || existing?.author || "Nigson Editorial",
      readMinutes: existing?.readMinutes || Math.max(3, Math.ceil((ap.excerpt || "").split(" ").length / 30)),
      excerpt: ap.excerpt || existing?.excerpt || "",
      img: ap.image || ap.img || existing?.img || blogMarket,
      body: bodyParas,
      sections,
    });
  }

  // Include default POSTS if not managed in admin or if admin posts list didn't include them
  for (const p of POSTS) {
    if (!processedSlugs.has(p.slug)) {
      const apMatch = adminPosts.find((ap) => (ap.slug || slugify(ap.title)) === p.slug);
      if (!apMatch || apMatch.status === "Published") {
        results.push(p);
        processedSlugs.add(p.slug);
      }
    }
  }

  return results;
}

export function getPost(slug: string) {
  if (!slug) return undefined;
  const decoded = decodeURIComponent(slug);
  const posts = getAllPublishedPosts();
  return posts.find((p) => p.slug === slug || p.slug === decoded);
}
