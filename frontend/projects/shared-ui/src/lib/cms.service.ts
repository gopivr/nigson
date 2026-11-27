import { Injectable, signal, computed, inject, NgZone } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of, switchMap, map, catchError, tap, defer, throwError, finalize, forkJoin } from 'rxjs';
import { SessionService } from './session.service';















/* ---------------------------------- types --------------------------------- */
/**
 * Backend Content DTOs (API-076, API-079 to API-082 - TASK-62, TASK-63)
 */
export interface ContentDTO {
  id: string;
  type: string;
  slug: string;
  locale: string;
  revision: number;
  title: string;
  body: {
    schemaVersion: number;
    blocks: any[];
  };
  metadata: {
    metaTitle: string;
    metaDescription: string;
    keywords?: string[];
  };
  publishedAt: string;
}

export interface ContentRevisionEdit {
  title: string;
  body: {
    schemaVersion: 1;
    blocks: any[];
  };
  metadata: {
    metaTitle: string;
    metaDescription: string;
    keywords?: string[];
  };
}


export type FieldType = "text" | "textarea" | "image" | "link";
export type FieldDef = { key: string; label: string; type: FieldType };

export type SectionDef = {
  id: string;
  title: string;
  note?: string;
  fields: FieldDef[];
  defaults: Record<string, string>;
  cardsLabel?: string;
  cardFields?: FieldDef[];
  defaultCards?: Record<string, string>[];
};

export type PageDef = { id: string; name: string; path: string; sections: SectionDef[] };

export type CmsCard = { id: string; fields: Record<string, string> };
export type SectionContent = { id: string; visible: boolean; fields: Record<string, string>; cards: CmsCard[] };
export type PageContent = { id: string; status: "Published" | "Draft"; updatedAt: string; sections: SectionContent[] };

/* -------------------------------- field kits ------------------------------- */

const F = {
  eyebrow: { key: "eyebrow", label: "Eyebrow", type: "text" } as FieldDef,
  heading: { key: "heading", label: "Heading", type: "text" } as FieldDef,
  body: { key: "body", label: "Description", type: "textarea" } as FieldDef,
  image: { key: "image", label: "Image", type: "image" } as FieldDef,
  btnLabel: { key: "btnLabel", label: "Button label", type: "text" } as FieldDef,
  btnLink: { key: "btnLink", label: "Button link", type: "link" } as FieldDef,
};

const cardTitleBody: FieldDef[] = [
  { key: "title", label: "Title", type: "text" },
  { key: "body", label: "Description", type: "textarea" },
];

/* --------------------------------- schemas -------------------------------- */

export const PAGES: PageDef[] = [
  {
    id: "home",
    name: "Home",
    path: "/",
    sections: [
      {
        id: "hero",
        title: "Hero",
        note: "Main homepage hero banner — full-height split layout.",
        fields: [
          { key: "badge", label: "Badge / Eyebrow text", type: "text" },
          { key: "heading", label: "Heading (Line 1)", type: "text" },
          { key: "headingAccent", label: "Heading (Accent / Line 2)", type: "text" },
          F.body,
          { key: "btn1Label", label: "Button 1 label", type: "text" },
          { key: "btn1Link", label: "Button 1 link", type: "link" },
          { key: "btn2Label", label: "Button 2 label", type: "text" },
          { key: "btn2Link", label: "Button 2 link", type: "link" },
        ],
        defaults: {
          badge: "Nigson",
          heading: "POWER YOUR",
          headingAccent: "EVERYDAY.",
          body: "Smart technology, essential accessories and everyday products designed for the way you live.",
          btn1Label: "Shop Now",
          btn1Link: "/products",
          btn2Label: "Explore Products",
          btn2Link: "#categories",
        },
      },
      {
        id: "newArrivals",
        title: "New Arrivals",
        note: "Full-width editorial section below the hero.",
        fields: [
          { key: "heading", label: "Heading", type: "text" },
          { key: "subtitle", label: "Subtitle", type: "textarea" },
          { key: "btnLabel", label: "Link label", type: "text" },
          { key: "btnLink", label: "Link destination", type: "link" },
        ],
        defaults: {
          heading: "MADE FOR EVERY DAY.",
          subtitle: "Thoughtfully selected technology and accessories that combine everyday usefulness with modern design.",
          btnLabel: "Explore New Arrivals",
          btnLink: "/products",
        },
      },
      {
        id: "categoryShowcase",
        title: "Category Showcase",
        note: "Alternating full-width image + text blocks, one per category. Each card is one block.",
        fields: [],
        defaults: {},
        cardsLabel: "Category blocks",
        cardFields: [
          { key: "label", label: "Eyebrow label", type: "text" },
          { key: "title", label: "Heading", type: "text" },
          { key: "description", label: "Description", type: "textarea" },
          { key: "filter", label: "Category filter (for /products?category=)", type: "text" },
          { key: "image", label: "Image path", type: "image" },
        ],
        defaultCards: [
          { label: "Audio", title: "Sound that moves with you.", description: "Immersive listening, made effortless.", filter: "Earbuds", image: "/assets/home-category-audio.png" },
          { label: "Power", title: "Ready when you need it.", description: "Dependable portable power for every day.", filter: "Power Banks", image: "/assets/home-category-power.png" },
          { label: "Charging", title: "Fast power. Less waiting.", description: "Compact chargers and cables built to keep up.", filter: "Chargers", image: "/assets/home-category-charging.png" },
          { label: "Mobile Accessories", title: "Made for your mobile life.", description: "The useful details that make every device better.", filter: "Cables", image: "/assets/home-category-mobile-accessories.png" },
          { label: "Car Accessories", title: "Upgrade every drive.", description: "Smarter essentials for the road ahead.", filter: "Power and Accessories", image: "/assets/home-category-car-accessories.png" },
          { label: "Home & Power", title: "Everyday power, simplified.", description: "Reliable essentials for a more connected home.", filter: "Extension Sockets", image: "/assets/nigson-hero-products.jpg" },
          { label: "FMCG", title: "The everyday, considered.", description: "Useful products selected for modern routines.", filter: "FMCG", image: "/assets/home-category-fmcg.png" },
        ],
      },
      {
        id: "lifestylePanels",
        title: "Lifestyle Panels",
        note: "Two large full-bleed image panels shown after the Charging category block.",
        fields: [],
        defaults: {},
        cardsLabel: "Panels",
        cardFields: [
          { key: "title", label: "Heading", type: "text" },
          { key: "text", label: "Body text", type: "textarea" },
          { key: "image", label: "Image path", type: "image" },
          { key: "alt", label: "Image alt text", type: "text" },
          { key: "btnLabel", label: "Button label", type: "text" },
          { key: "btnLink", label: "Button link", type: "link" },
        ],
        defaultCards: [
          { title: "YOUR WORLD. YOUR SOUND.", text: "Turn everyday moments into your own soundtrack.", image: "/assets/nigson-lifestyle-sound.jpg", alt: "Person relaxing with Nigson wireless headphones", btnLabel: "SHOP NOW", btnLink: "#best-sellers" },
          { title: "POWER YOUR WORKDAY.", text: "Stay connected, charged and ready for what comes next.", image: "/assets/nigson-lifestyle-work.jpg", alt: "Person working with a Nigson power bank", btnLabel: "SHOP NOW", btnLink: "#best-sellers" },
        ],
      },
      {
        id: "bestSellers",
        title: "Best Sellers",
        note: "Grid of deal products customers keep coming back for.",
        fields: [
          { key: "eyebrow", label: "Eyebrow", type: "text" },
          { key: "heading", label: "Heading", type: "text" },
        ],
        defaults: {
          eyebrow: "Customer Favourites",
          heading: "THE PRODUCTS PEOPLE COME BACK FOR.",
        },
      },
      {
        id: "wholesale",
        title: "Nigson Wholesale",
        note: "Dark full-width split banner for wholesale/business customers.",
        fields: [
          { key: "eyebrow", label: "Eyebrow", type: "text" },
          { key: "heading", label: "Heading", type: "text" },
          { key: "body", label: "Body text", type: "textarea" },
          { key: "btn1Label", label: "Button 1 label", type: "text" },
          { key: "btn1Link", label: "Button 1 link", type: "link" },
          { key: "btn2Label", label: "Button 2 label (optional)", type: "text" },
          { key: "btn2Link", label: "Button 2 link", type: "link" },
        ],
        defaults: {
          eyebrow: "NIGSON WHOLESALE",
          heading: "BUY MORE. BUILD MORE.",
          body: "Special pricing and dependable supply for retailers, businesses and bulk buyers.",
          btn1Label: "SHOP WHOLESALE",
          btn1Link: "/distributor",
          btn2Label: "REQUEST A QUOTE",
          btn2Link: "/quote",
        },
      },
      {
        id: "distributorCta",
        title: "Become a Partner",
        note: "Light-background split section for partner/distributor CTA.",
        fields: [
          { key: "eyebrow", label: "Eyebrow", type: "text" },
          { key: "heading", label: "Heading", type: "text" },
          { key: "body", label: "Body text", type: "textarea" },
          { key: "btnLabel", label: "Button label", type: "text" },
          { key: "btnLink", label: "Button link", type: "link" },
        ],
        defaults: {
          eyebrow: "PARTNERSHIP",
          heading: "BECOME A NIGSON PARTNER.",
          body: "Grow your business with Nigson through reliable products, dependable supply and long-term partnership.",
          btnLabel: "BECOME A PARTNER",
          btnLink: "/distributor",
        },
        cardsLabel: "Benefit tags",
        cardFields: [{ key: "title", label: "Benefit", type: "text" }],
        defaultCards: [
          { title: "Exclusive Pricing" },
          { title: "Marketing Support" },
          { title: "Reliable Supply" },
          { title: "Dedicated Sales Team" },
        ],
      },
      {
        id: "serviceBenefits",
        title: "Service Benefits",
        note: "4-column benefits strip — icons are fixed, titles and descriptions are editable.",
        fields: [
          { key: "heading", label: "Section heading", type: "text" },
        ],
        defaults: {
          heading: "BUILT AROUND YOUR EVERYDAY.",
        },
        cardsLabel: "Benefits (exactly 4)",
        cardFields: [
          { key: "title", label: "Benefit title", type: "text" },
          { key: "desc", label: "Description", type: "text" },
        ],
        defaultCards: [
          { title: "Fast & Reliable Delivery", desc: "Across Nigeria, with care." },
          { title: "Secure Payments", desc: "Protected at every step." },
          { title: "Easy Returns", desc: "Simple, straightforward support." },
          { title: "Customer Support", desc: "Real help when you need it." },
        ],
      },
      {
        id: "testimonials",
        title: "Testimonials",
        note: "Customer testimonials shown as quote cards.",
        fields: [
          { key: "eyebrow", label: "Eyebrow", type: "text" },
          { key: "heading", label: "Heading", type: "text" },
        ],
        defaults: {
          eyebrow: "TESTIMONIALS",
          heading: "Trusted by the people who move Nigerian retail.",
        },
        cardsLabel: "Testimonials",
        cardFields: [
          { key: "quote", label: "Quote", type: "textarea" },
          { key: "name", label: "Name", type: "text" },
          { key: "role", label: "Role / Company", type: "text" },
        ],
        defaultCards: [
          { quote: "Nigson has been our go-to for phone accessories for over 6 years. Consistent stock, honest pricing — they simply deliver.", name: "Chidi O.", role: "Onitsha Wholesaler" },
          { quote: "Their turnaround on large orders is unmatched. Their logistics simply works, and we haven't had a stock issue in years.", name: "Amaka N.", role: "Supermarket Chain Buyer" },
          { quote: "Quality is genuine and predictable. That's rare in this market — and it's why our shelves keep moving.", name: "Bola A.", role: "Retail Chain Manager" },
        ],
      },
      {
        id: "home_seo",
        title: "Discover Nigson (SEO)",
        note: "Large centred CTA / SEO section near the bottom.",
        fields: [
          { key: "eyebrow", label: "Eyebrow", type: "text" },
          { key: "heading", label: "Heading", type: "text" },
          { key: "body", label: "Body", type: "textarea" },
          { key: "btnLabel", label: "Button label", type: "text" },
          { key: "btnLink", label: "Button link", type: "link" },
        ],
        defaults: {
          eyebrow: "Discover Nigson",
          heading: "GOOD PRODUCTS. SIMPLER CHOICES.",
          body: "Discover technology, accessories and everyday essentials selected for modern living.",
          btnLabel: "Shop Nigson",
          btnLink: "/products",
        },
      },
      {
        id: "featuredCarousel",
        title: "Featured Carousel",
        note: "Auto-advancing draggable carousel at the bottom of the home page. Each card is one slide.",
        fields: [],
        defaults: {},
        cardsLabel: "Slides",
        cardFields: [
          { key: "kicker", label: "Kicker (small top text)", type: "text" },
          { key: "title", label: "Slide heading", type: "text" },
          { key: "desc", label: "Description", type: "textarea" },
          { key: "cta", label: "Button label", type: "text" },
          { key: "link", label: "Button link", type: "link" },
          { key: "category", label: "Category filter (for /products?category=)", type: "text" },
          { key: "tone", label: "Colour tone (dark / light)", type: "text" },
          { key: "image", label: "Background image path", type: "image" },
        ],
        defaultCards: [
          { kicker: "Nigson Audio", title: "SOUND. SIMPLIFIED.", desc: "Wireless audio made for every moment.", cta: "Shop Audio", link: "/products", category: "Earbuds", tone: "dark", image: "/assets/nigson-audio.jpg" },
          { kicker: "Nigson Power", title: "POWER YOUR EVERYDAY.", desc: "Reliable charging essentials, wherever you go.", cta: "Shop Power", link: "/products", category: "Power Banks", tone: "light", image: "/assets/nigson-power.jpg" },
          { kicker: "Mobile Essentials", title: "READY FOR EVERY DAY.", desc: "Smart accessories designed around your devices.", cta: "Explore Accessories", link: "/products", category: "Cables", tone: "light", image: "/assets/nigson-hero-products.jpg" },
          { kicker: "Nigson Deals", title: "MORE VALUE. LESS WAITING.", desc: "Discover limited-time offers across Nigson.", cta: "View Deals", link: "/products", category: "", tone: "dark", image: "/assets/nigson-wholesale.jpg" },
        ],
      },
      {
        id: "footer",
        title: "Footer",
        note: "Site-wide footer navigation and company information.",
        fields: [
          { key: "tagline", label: "Tagline", type: "textarea" },
          { key: "copyright", label: "Copyright line", type: "text" },
          { key: "motto", label: "Footer motto", type: "text" },
          { key: "credit", label: "Developer credit", type: "text" },
          { key: "creditLink", label: "Developer credit link", type: "link" },
        ],
        defaults: {
          tagline: "Building Value. Delivering Excellence. Creating Lasting Impact.",
          copyright: "Nigson Group of Companies. All rights reserved.",
          motto: "Innovation • Quality • Trust",
          credit: "Develop & Design By Techxplorers Pvt. Ltd",
          creditLink: "https://techxplorers.in/",
        },
      },
    ],
  },

  {
    id: "about",
    name: "About",
    path: "/about",
    sections: [
      {
        id: "hero",
        title: "Hero",
        fields: [F.eyebrow, F.heading, F.body],
        defaults: {
          eyebrow: "About Nigson Group",
          heading: "A three-decade Nigerian legacy, built for the next generation.",
          body: "Founded by the late Chief Ignatius Nwabueze Odunukwe, Nigson has grown from a modest trading business into a diversified enterprise spanning importation, distribution, consumer products and real estate.",
        },
      },
      {
        id: "story",
        title: "Company story",
        fields: [
          { key: "p1", label: "Paragraph 1", type: "textarea" },
          { key: "p2", label: "Paragraph 2", type: "textarea" },
          { key: "p3", label: "Paragraph 3", type: "textarea" },
          { key: "smartEyebrow", label: "Side card eyebrow", type: "text" },
          { key: "smartTitle", label: "Side card title", type: "text" },
        ],
        defaults: {
          p1: "Nigson Imports and Investment Company Limited is a proudly Nigerian-owned enterprise with a rich legacy spanning over three decades. From a modest spare parts business, the company steadily expanded into personal care under the Zarina brand, household goods, FMCG, mobile technology accessories and energy solutions.",
          p2: "Following the passing of its founder in 2019, the business entered a new chapter under the leadership of Miss Ijeoma Odunukwe. It was restructured into two complementary subsidiaries: Nigson Imports & Investment Company Limited and Nigson Properties Limited — reflecting a broader vision for diversification, innovation and long-term growth.",
          p3: "Today, Nigson stands as a symbol of resilience, growth and generational continuity — committed to strengthening its presence across Nigeria, expanding throughout Africa, and positioning itself as a globally recognized Nigerian enterprise.",
          smartEyebrow: "Think Nigson. Think",
          smartTitle: "S.M.A.R.T",
        },
        cardsLabel: "S.M.A.R.T lines",
        cardFields: [{ key: "title", label: "Line", type: "text" }],
        defaultCards: [
          { title: "S — Service Excellence" },
          { title: "M — Market Leadership" },
          { title: "A — Accountability" },
          { title: "R — Reliability" },
          { title: "T — Technology & Innovation" },
        ],
      },
      {
        id: "mission",
        title: "Purpose / Vision / Mission",
        fields: [],
        defaults: {},
        cardsLabel: "Statements",
        cardFields: cardTitleBody,
        defaultCards: [
          { title: "Our Purpose", body: "To enrich lives and create lasting value by delivering quality products, services and innovative solutions that meet the everyday needs of individuals, families and businesses." },
          { title: "Our Vision", body: "To become a leading diversified enterprise in Nigeria and Africa — delivering innovative products, premium real estate and exceptional services for generations to come." },
          { title: "Our Mission", body: "To provide high-quality products, world-class real estate developments and customer-centric services through innovation, professionalism and operational excellence." },
        ],
      },
      {
        id: "journey",
        title: "Our Journey (timeline)",
        fields: [F.eyebrow, F.heading],
        defaults: { eyebrow: "Our Journey", heading: "Three decades. One promise, kept." },
        cardsLabel: "Milestones",
        cardFields: [
          { key: "year", label: "Year", type: "text" },
          { key: "title", label: "Title", type: "text" },
          { key: "body", label: "Description", type: "textarea" },
        ],
        defaultCards: [
          { year: "1993", title: "Founded in Lagos", body: "Chief Ignatius Nwabueze Odunukwe founds Nigson as a modest spare-parts trading business." },
          { year: "2005", title: "Nationwide distribution", body: "Expansion into personal care under the Zarina brand and household goods, reaching dealers across Nigeria." },
          { year: "2014", title: "Mobile & power solutions", body: "Nigson enters the phone accessories and energy solutions market with premium imports." },
          { year: "2019", title: "Second-generation leadership", body: "Following the founder's passing, the group is restructured under Miss Ijeoma Odunukwe into two subsidiaries." },
          { year: "2022", title: "Nigson Properties launched", body: "Landmark residential developments across Lekki, Ologolo and Ikoyi begin delivery." },
          { year: "2026", title: "Pan-African expansion", body: "The group scales operations and positions Nigson as a globally recognised Nigerian enterprise." },
        ],
      },
      {
        id: "values",
        title: "Core Values",
        fields: [F.eyebrow, F.heading],
        defaults: { eyebrow: "Core Values", heading: "The principles behind every decision." },
        cardsLabel: "Values",
        cardFields: cardTitleBody,
        defaultCards: [
          { title: "Professionalism", body: "Best-in-class standards, sound judgement and ethics." },
          { title: "Excellence", body: "Exceptional, uncommon, superior services and results." },
          { title: "Trust", body: "We remain trustworthy — every promise is fulfilled." },
          { title: "Integrity", body: "Our word is our bond. We live up to our commitments." },
          { title: "Value-driven", body: "We relentlessly seek new ways to create and deliver value." },
          { title: "Empathy", body: "We address the real pain points of the clients we serve." },
          { title: "Resilience", body: "Flexible and committed to co-creating solutions." },
        ],
      },
      {
        id: "leadership",
        title: "Leadership / Directors",
        fields: [F.eyebrow, F.heading, F.body],
        defaults: {
          eyebrow: "Leadership / Directors",
          heading: "Meet the team.",
          body: "A dynamic team of second-generation leaders committed to preserving the founder's legacy while driving innovation and growth.",
        },
        cardsLabel: "Members",
        cardFields: [
          { key: "name", label: "Name", type: "text" },
          { key: "role", label: "Position", type: "text" },
          { key: "bio", label: "Description", type: "textarea" },
          { key: "image", label: "Photo", type: "image" },
        ],
        defaultCards: [
          { name: "Miss Ijeoma Odunukwe", role: "Managing Director & Chief Executive Officer", bio: "Provides strategic leadership across the Group's corporate growth, business development, investments and organizational transformation. Lagos Business School OMP alumna and member of the Institute of Directors.", image: "/assets/Ijeoma_odunukwe_img.jpeg" },
          { name: "Miss Nkem Odunukwe", role: "Director, Imports & Investments", bio: "Leads importation, sourcing, procurement and distribution. Babcock University graduate and Lagos Business School OMP alumna focused on strategic sourcing and supply chain excellence.", image: "/assets/Nkem_Odunukwe.jpeg" },
          { name: "Diana Odunukwe", role: "Director, Properties", bio: "", image: "" },
          { name: "Odinaka Odunukwe", role: "Director, Operations / Properties", bio: "Directs Nigson Properties — acquisition, development, sales strategy and portfolio expansion. B.Sc Pan-Atlantic University; Certified Real Estate Financial Modeling & Investment Analysis Professional.", image: "/assets/Odinaka_Odunukweimg.jpeg" },
        ],
      },
      {
        id: "management",
        title: "Management Team",
        fields: [F.eyebrow, F.heading, F.body],
        defaults: { eyebrow: "Management Team", heading: "Management Team.", body: "" },
        cardsLabel: "Members",
        cardFields: [
          { key: "name", label: "Name", type: "text" },
          { key: "role", label: "Position", type: "text" },
          { key: "bio", label: "Description", type: "textarea" },
          { key: "image", label: "Photo", type: "image" },
        ],
        defaultCards: [
          { name: "Taiwo Olubunmi Kobamije", role: "Group Head, Finance & Accounts", bio: "Provides strategic financial leadership across Nigson Group, overseeing financial planning, governance, reporting, and financial performance to support sustainable growth.", image: "/assets/taiwo-olubunmi-kobamije.png" },
          { name: "Fatai Damilola Balogun", role: "Head, Human Resources Management", bio: "Leads Nigson Group's human capital strategy, talent development, workforce planning, and organizational culture to build high-performing teams.", image: "" },
          { name: "Adedamola Adeyemi, PMP", role: "Head, Projects & Operations – Nigson Properties", bio: "Leads project delivery and operations at Nigson Properties, overseeing real estate projects, process optimization, quality, and client satisfaction.", image: "" },
          { name: "Ladeinde Olubukola", role: "Head of Sales & Marketing", bio: "Leads sales, marketing, and business development across Nigson Group, driving revenue growth, brand positioning, partnerships, and market expansion.", image: "" },
        ],
      },
      {
        id: "advisory",
        title: "Advisory Board",
        fields: [F.eyebrow, F.heading, F.body],
        defaults: { eyebrow: "Advisory Board", heading: "Advisory Board.", body: "" },
        cardsLabel: "Members",
        cardFields: [
          { key: "name", label: "Name", type: "text" },
          { key: "role", label: "Position", type: "text" },
          { key: "bio", label: "Description", type: "textarea" },
          { key: "image", label: "Photo", type: "image" },
        ],
        defaultCards: [
          { name: "Chidi Okoro", role: "Board Adviser", bio: "Provides strategic counsel on business growth, operational excellence, and corporate governance, drawing on over 30 years of executive leadership experience.", image: "" },
          { name: "Professor Nkemdilim Iheanachor", role: "Board Adviser", bio: "Provides strategic guidance on business growth, innovation, digital transformation, and long-term value creation, with expertise in strategy and international business.", image: "" },
        ],
      },
      {
        id: "careersCta",
        title: "Join our Team CTA",
        fields: [F.eyebrow, F.heading, F.body, F.btnLabel, F.btnLink],
        defaults: {
          eyebrow: "Careers",
          heading: "Join our Team.",
          body: "Build a career at one of Nigeria's most trusted diversified enterprises. Explore open roles across imports, distribution, properties and corporate functions.",
          btnLabel: "View open positions",
          btnLink: "/careers",
        },
        cardsLabel: "Highlights",
        cardFields: cardTitleBody,
        defaultCards: [
          { title: "Full-time roles", body: "Grow across a diversified group" },
          { title: "Internships", body: "Learn from experienced leaders" },
          { title: "Creators", body: "Collaborate on campaigns" },
          { title: "Affiliates", body: "Earn with every referral" },
        ],
      },
    ],
  },

  {
    id: "insights",
    name: "Insights",
    path: "/insights",
    sections: [
      {
        id: "hero",
        title: "Hero",
        fields: [F.eyebrow, F.heading, F.body],
        defaults: {
          eyebrow: "Insights",
          heading: "Latest from Nigson.",
          body: "Product updates, industry trends and business news.",
        },
      },
      {
        id: "articles",
        title: "Insights Articles & Content",
        note: "Edit and manage the Insight articles, section headings, and bullet points displayed on this page.",
        fields: [
          { key: "intro", label: "Section intro", type: "textarea" },
          { key: "readLabel", label: "Read link label", type: "text" },
        ],
        defaults: { intro: "", readLabel: "Read article" },
      },
      {
        id: "cta",
        title: "CTA",
        fields: [F.heading, F.body, F.btnLabel, F.btnLink],
        defaults: {
          heading: "Want wholesale pricing on what you just read about?",
          body: "Our sales desk responds to every quote request within 24 hours.",
          btnLabel: "Request a quote",
          btnLink: "/quote",
        },
      },
    ],
  },

  {
    id: "contact",
    name: "Contact",
    path: "/contact",
    sections: [
      {
        id: "hero",
        title: "Hero",
        fields: [F.eyebrow, F.heading, F.body],
        defaults: {
          eyebrow: "Get in touch",
          heading: "Talk to Nigson.",
          body: "Sales, partnerships, or a dedicated complaints channel — we're here.",
        },
      },
      {
        id: "info",
        title: "Contact information",
        fields: [
          { key: "officeTitle", label: "Office title", type: "text" },
          { key: "address", label: "Address", type: "textarea" },
          { key: "phoneTitle", label: "Phone title", type: "text" },
          { key: "phone", label: "Phone number", type: "text" },
          { key: "emailTitle", label: "Email title", type: "text" },
          { key: "email", label: "Email address", type: "text" },
          { key: "whatsappTitle", label: "WhatsApp title", type: "text" },
          { key: "whatsappBody", label: "WhatsApp text", type: "text" },
          { key: "whatsappLink", label: "WhatsApp link", type: "link" },
        ],
        defaults: {
          officeTitle: "Corporate office",
          address: "Plot 30B, Oladimeji Alo Street,\nOff Freedom Way, Ikate, Lekki, Lagos.",
          phoneTitle: "Phone",
          phone: "+234 807 346 7809",
          emailTitle: "Email",
          email: "info@nigson.com",
          whatsappTitle: "Chat on WhatsApp",
          whatsappBody: "Fastest way to reach sales.",
          whatsappLink: "https://wa.me/2348073467809",
        },
      },
      {
        id: "form",
        title: "Contact form",
        fields: [
          { key: "heading", label: "Form heading", type: "text" },
          { key: "nameLabel", label: "Name label", type: "text" },
          { key: "emailLabel", label: "Email label", type: "text" },
          { key: "subjectLabel", label: "Subject label", type: "text" },
          { key: "messageLabel", label: "Message label", type: "text" },
          { key: "btnLabel", label: "Submit button", type: "text" },
          { key: "successText", label: "Success message", type: "text" },
        ],
        defaults: {
          heading: "Send us a message",
          nameLabel: "Full name",
          emailLabel: "Email",
          subjectLabel: "Subject",
          messageLabel: "Message",
          btnLabel: "Send message",
          successText: "Thank you — we'll be in touch within 24 hours.",
        },
      },
    ],
  },

  {
    id: "distributor",
    name: "Distributor",
    path: "/distributor",
    sections: [
      {
        id: "hero",
        title: "Hero",
        fields: [F.eyebrow, F.heading, F.body],
        defaults: {
          eyebrow: "Partner with Nigson",
          heading: "Become a Nigson distributor.",
          body: "Join Nigeria's most reliable distribution network for phone accessories, power solutions and FMCG.",
        },
      },
      {
        id: "benefits",
        title: "Benefits",
        fields: [{ key: "heading", label: "Heading", type: "text" }],
        defaults: { heading: "What you get" },
        cardsLabel: "Benefits",
        cardFields: [{ key: "title", label: "Benefit", type: "text" }],
        defaultCards: [
          { title: "Exclusive wholesale pricing" },
          { title: "Priority access to new SKUs & launches" },
          { title: "Reliable, consistent supply" },
          { title: "Marketing and merchandising support" },
          { title: "Dedicated sales rep" },
        ],
      },
      {
        id: "form",
        title: "Registration form",
        fields: [
          { key: "heading", label: "Form heading", type: "text" },
          { key: "body", label: "Form intro", type: "textarea" },
          { key: "btnLabel", label: "Submit button", type: "text" },
          { key: "successText", label: "Success message", type: "text" },
        ],
        defaults: {
          heading: "Distributor application",
          body: "Tell us about your business and our team will review your application.",
          btnLabel: "Submit application",
          successText: "Application received — we'll review and get back to you.",
        },
      },
    ],
  },

  {
    id: "careers",
    name: "Careers",
    path: "/careers",
    sections: [
      {
        id: "hero",
        title: "Hero",
        fields: [F.eyebrow, F.heading, F.body],
        defaults: {
          eyebrow: "Join our team",
          heading: "Build a career at Nigson.",
          body: "We're growing across imports, real estate and consumer brands — and we're looking for ambitious people to grow with us.",
        },
      },
      {
        id: "tracks",
        title: "Company introduction / tracks",
        fields: [],
        defaults: {},
        cardsLabel: "Tracks",
        cardFields: cardTitleBody,
        defaultCards: [
          { title: "Full-time roles", body: "Grow with a diversified enterprise across imports, distribution and properties." },
          { title: "Internships", body: "Learn from experienced leaders while building real business impact." },
          { title: "Creators & influencers", body: "Collaborate on product launches and campaigns across our channels." },
          { title: "Affiliate marketers", body: "Earn commission promoting Nigson's product range." },
        ],
      },
      {
        id: "openings",
        title: "Job listings",
        fields: [
          { key: "heading", label: "Heading", type: "text" },
          { key: "btnLabel", label: "Apply button label", type: "text" },
          { key: "applyEmail", label: "Apply email", type: "text" },
        ],
        defaults: { heading: "Open positions", btnLabel: "Apply now", applyEmail: "careers@nigson.com" },
        cardsLabel: "Openings",
        cardFields: [
          { key: "title", label: "Role", type: "text" },
          { key: "type", label: "Type", type: "text" },
          { key: "location", label: "Location", type: "text" },
        ],
        defaultCards: [
          { title: "Sales Executive — Phone Accessories", type: "Full-time", location: "Lagos" },
          { title: "Warehouse Operations Manager", type: "Full-time", location: "Lagos" },
          { title: "Digital Marketing Associate", type: "Full-time", location: "Lagos / Remote" },
          { title: "Business Development Intern", type: "Internship", location: "Lagos" },
        ],
      },
    ],
  },
];

export const pageDef = (id: string) => PAGES.find((p) => p.id === id);
export const sectionDef = (pageId: string, sectionId: string) => pageDef(pageId)?.sections.find((s) => s.id === sectionId);

/* -------------------------------- defaults -------------------------------- */

const defaultPage = (def: PageDef): PageContent => ({
  id: def.id,
  status: "Draft",
  updatedAt: new Date(0).toISOString(),
  sections: def.sections.map((s) => ({
    id: s.id,
    visible: true,
    fields: { ...s.defaults },
    cards: (s.defaultCards ?? []).map((c, i) => ({ id: `${s.id}_c${i}`, fields: { ...c } })),
  })),
});

const defaultStore = (): Record<string, PageContent> =>
  Object.fromEntries(PAGES.map((p) => [p.id, defaultPage(p)]));

/* -------------------------------- service -------------------------------- */

const KEY = "nigson_cms_v4";

export type CmsState = { published: Record<string, PageContent>; drafts: Record<string, PageContent> };

const clone = <T,>(v: T): T => JSON.parse(JSON.stringify(v));

function mergeWithDefaults(saved: Partial<CmsState> | null): CmsState {
  const base: CmsState = { published: defaultStore(), drafts: defaultStore() };
  if (!saved) return base;
  for (const key of ["published", "drafts"] as const) {
    const bucket = saved[key];
    if (!bucket) continue;
    for (const def of PAGES) {
      const s = bucket[def.id];
      if (!s) continue;
      const fallback = base[key][def.id];
      base[key][def.id] = {
        id: def.id,
        status: s.status === "Draft" ? "Draft" : "Published",
        updatedAt: s.updatedAt ?? fallback.updatedAt,
        sections: def.sections.map((sd) => {
          const found = (s.sections ?? []).find((x) => x.id === sd.id);
          const fb = fallback.sections.find((x) => x.id === sd.id)!;
          if (!found) return fb;
          return {
            id: sd.id,
            visible: found.visible !== false,
            fields: { ...fb.fields, ...(found.fields ?? {}) },
            cards: Array.isArray(found.cards) ? found.cards : fb.cards,
          };
        }).sort((a, b) => {
          const order = (s.sections ?? []).map((x) => x.id);
          const ia = order.indexOf(a.id), ib = order.indexOf(b.id);
          return (ia === -1 ? 999 : ia) - (ib === -1 ? 999 : ib);
        }),
      };
    }
  }
  return base;
}

export type SectionApi = {
  id: string;
  visible: boolean;
  t: (key: string) => string;
  cards: <T>(mapper: (fields: Record<string, string>, index: number) => T) => T[];
};

@Injectable({providedIn:'root'})
export class CmsService {
  private http=inject(HttpClient);
  private session=inject(SessionService);
  readonly canPublish=computed(()=>this.session.isAdmin()||(this.session.hasRole('CONTENT_EDITOR')&&this.session.hasRole('CONTENT_PUBLISHER')));
  readonly isPublishing=signal(false);
  readonly error=signal('');
  readonly state=signal<CmsState>({published:defaultStore(),drafts:defaultStore()});
  readonly hydrated=signal(false);
  readonly drafts=computed(()=>this.state().drafts);
  readonly published=computed(()=>this.state().published);
  private records:any[]=[];
  constructor(){ this.syncFromDatabase(); }
  message(e:any){return e?.error?.detail || e?.message || 'Content request failed.';}
  private headers(version?:number){
    if(version!==undefined && !Number.isSafeInteger(version)) throw new Error('Reload the content before saving: current version is missing.');
    const csrf = this.session.csrfToken();
    return {
      'Idempotency-Key':crypto.randomUUID(),
      ...(csrf ? {'X-CSRF-TOKEN': csrf} : {}),
      ...(version===undefined?{}:{'If-Match':`"${version}"`})
    };
  }
  private list(path:string,page=0):Observable<any[]> {
    return this.http.get<any>(`${path}${path.includes('?')?'&':'?'}page=${page}&size=100`).pipe(switchMap(r=>{
      const rows=r.items??r.content;
      if(!Array.isArray(rows))throw new Error('Invalid content list response.');
      return page+1<r.totalPages?this.list(path,page+1).pipe(map(next=>[...rows,...next])):of(rows);
    }));
  }
  loadStaffContent():Observable<any[]> {return this.list('/api/v1/staff/content').pipe(tap(rows=>this.records=rows));}
  private type(id:string){return ({home:'HOME',about:'ABOUT',contact:'CONTACT',careers:'CAREERS',insights:'HOME',distributor:'DIVISION'} as Record<string,string>)[id] || 'DIVISION';}
  private decode(row:any):PageContent|null {
    try{const sections=(row.body?.blocks||[]).filter((b:any)=>b.type==='TEXT').map((b:any)=>JSON.parse(b.text));
      if(!sections.length || !sections.every((s:any)=>s.id && s.fields && Array.isArray(s.cards)))return null;
      return {id:row.slug,status:row.state==='PUBLISHED'||row.publishedAt?'Published':'Draft',updatedAt:row.publishedAt||'',sections};
    }catch{return null;}
  }
  syncFromDatabase():void {
    forkJoin([...new Set(PAGES.map(page=>this.type(page.id)))].map(type=>this.list('/api/v1/content?locale=en&type='+type))).pipe(map(pages=>pages.flat())).subscribe({next:rows=>{
      const next=clone(this.state());for(const r of rows){const page=this.decode(r);if(page&&pageDef(page.id)){next.published[page.id]=page;next.drafts[page.id]=clone(page);}}
      this.state.set(next);this.hydrated.set(true);
    },error:e=>{this.error.set(this.message(e));this.hydrated.set(true);}});
  }
  loadDrafts():Observable<any[]> {return this.loadStaffContent().pipe(tap(rows=>{
    const next=clone(this.state());for(const r of rows){const page=this.decode(r);if(page&&pageDef(page.id))next.drafts[page.id]=page;}this.state.set(next);
  }));}
  pageDef(id:string){return pageDef(id);}
  updateDraft(id:string,mutator:(p:PageContent)=>PageContent){const next=clone(this.state());if(next.drafts[id]){next.drafts[id]=mutator(next.drafts[id]);next.drafts[id].status='Draft';this.state.set(next);}}
  private pageEdit(id:string):ContentRevisionEdit {
    const def=pageDef(id),page=this.drafts()[id];if(!def||!page)throw new Error('Unknown content page.');
    return {title:def.name,body:{schemaVersion:1,blocks:page.sections.map(s=>({type:'TEXT',text:JSON.stringify(s)}))},metadata:{metaTitle:def.name+' | Nigson',metaDescription:'Official '+def.name+' page'}};
  }
  private edit(edit:ContentRevisionEdit){return {...edit,metadata:{title:edit.metadata.metaTitle.slice(0,70),description:edit.metadata.metaDescription.slice(0,180),canonicalUrl:null,robots:'INDEX',alternates:[],structuredData:{}}};}
  saveContent(type:string,slug:string,edit:any):Observable<any> {
    this.error.set('');
    return this.loadStaffContent().pipe(switchMap(rows=>{
      const existing=rows.find(r=>r.type===type&&r.slug===slug&&r.locale==='en');
      return existing?this.http.post<any>(`/api/v1/staff/content/${existing.contentItemId}/revisions`,edit,{headers:this.headers(existing.itemVersion)}):this.http.post<any>('/api/v1/staff/content',{type,slug,locale:'en',...edit},{headers:this.headers()});
    }),catchError(e=>{this.error.set(this.message(e));return throwError(()=>e);}));
  }
  saveToDatabase(id:string):Observable<boolean>{return defer(()=>this.saveContent(this.type(id),id,this.edit(this.pageEdit(id)))).pipe(map(()=>true));}
  saveDraft(id:string):Observable<boolean>{return this.saveToDatabase(id);}
  fetchContent(type:string,slug:string):Observable<ContentDTO|null>{return this.http.get<ContentDTO>(`/api/v1/content/${type.toUpperCase()}/${encodeURIComponent(slug)}?locale=en`);}
  private step(revision:any,path:string,body:any):Observable<any>{return this.http.post<any>(`/api/v1/staff/content-revisions/${revision.id}/${path}`,body,{headers:this.headers(revision.version)});}
  publishContent(revision:any):Observable<any>{
    if(!this.canPublish())return throwError(()=>new Error('Publishing requires both Content Editor and Content Publisher access in the current CMS workflow. Save a draft instead.'));
    let result=of(revision);
    if(revision.state==='DRAFT')result=result.pipe(switchMap(r=>this.step(r,'review',{reason:'Submitted from admin content editor'})));
    if(['DRAFT','REVIEW'].includes(revision.state))result=result.pipe(switchMap(r=>this.step(r,'approval',{approve:true,reason:'Approved from admin content editor'})));
    return result.pipe(switchMap(r=>r.state==='PUBLISHED'?of(r):this.step(r,'publication',{action:'PUBLISH',reason:'Published from admin content editor'})));
  }
  withdrawContent(revision:any):Observable<any>{return this.step(revision,'publication',{action:'WITHDRAW',reason:'Withdrawn from admin content editor'});}
  executePublishPipeline(id:string,edit?:ContentRevisionEdit):Observable<boolean>{
    if(!this.canPublish())return throwError(()=>new Error('Publishing requires Content Editor and Content Publisher access. Save a draft instead.'));
    this.isPublishing.set(true);return defer(()=>this.saveContent(this.type(id),id,this.edit(edit||this.pageEdit(id)))).pipe(switchMap(r=>this.publishContent(r)),tap(()=>{
      const next=clone(this.state());next.published[id]=clone(next.drafts[id]);next.published[id].status='Published';next.drafts[id].status='Published';this.state.set(next);
    }),map(()=>true),catchError(e=>{this.error.set(this.message(e));return throwError(()=>e);}),finalize(()=>this.isPublishing.set(false)));
  }
  getPageSections(id:string){
    const preview=typeof window!=='undefined'&&window.location.search.includes('preview=true');
    const page=computed(()=>preview?this.drafts()[id]:this.published()[id]);
    return {order:computed(()=>page()?.sections.map(s=>s.id)||[]),get:(sectionId:string):SectionApi=>{
      const s=page()?.sections.find(s=>s.id===sectionId);return {id:sectionId,visible:s?.visible??false,t:key=>s?.fields[key]||'',cards:<T>(mapper:(f:Record<string,string>,i:number)=>T)=>(s?.cards||[]).map((c,i)=>mapper(c.fields,i))};
    }};
  }
  loadBlogs():Observable<any[]>{return this.loadStaffContent().pipe(map(rows=>rows.filter(r=>r.type==='BLOG').map(r=>this.blog(r))));}
  private blog(r:any){const extra=r.metadata?.structuredData||{};return {...extra,id:r.contentItemId,revision:r,title:r.title,slug:r.slug,status:r.state==='PUBLISHED'?'Published':'Draft',body:(r.body?.blocks||[]).filter((b:any)=>b.type==='TEXT').map((b:any)=>b.text).join('\n\n'),tag:extra.tag||'',author:extra.author||'',date:r.publishedAt||extra.date||'',excerpt:extra.excerpt||'',image:extra.image||''};}
  saveBlog(post:any):Observable<any>{
    if(post.status==='Published'&&!this.canPublish())return throwError(()=>new Error('Publishing requires Content Editor and Content Publisher access. Save a draft instead.'));
    const cleanTitle = (post.title || 'Untitled Article').trim();
    const cleanSlug = (post.slug || cleanTitle).toLowerCase().trim().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
    const cleanExcerpt = (post.excerpt || cleanTitle || 'Article summary.').trim();
    const cleanBody = (post.body || post.excerpt || cleanTitle || 'Article content.').trim();
    const metaTitle = cleanTitle.slice(0, 70).trim() || 'Article';
    const metaDesc = (cleanExcerpt || cleanTitle).slice(0, 180).trim() || 'Article overview';

    const edit={
      title: cleanTitle,
      body:{
        schemaVersion: 1,
        blocks: [{ type: 'TEXT', text: cleanBody }]
      },
      metadata:{
        title: metaTitle,
        description: metaDesc,
        robots: 'INDEX',
        canonicalUrl: null,
        alternates: [],
        structuredData:{
          tag: (post.tag || 'Industry').trim(),
          author: (post.author || 'Nigson Team').trim(),
          excerpt: cleanExcerpt,
          image: (post.image || '/assets/blog-market.jpg').trim(),
          date: (post.date || new Date().toLocaleDateString('en-US', { month: 'short', year: 'numeric' })).trim()
        }
      }
    };
    return this.saveContent('BLOG', cleanSlug, edit).pipe(
      switchMap(r => post.status === 'Published' ? this.publishContent(r) : of(r)),
      map(r => this.blog(r))
    );
  }
}