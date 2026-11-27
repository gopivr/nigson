const cp = require('child_process');

const INSIGHTS_PAGE = {
  id: 'insights',
  status: 'Published',
  sections: [
    {
      id: 'hero',
      visible: true,
      fields: {
        eyebrow: 'Insights',
        heading: 'Latest from Nigson.',
        body: 'Product updates, industry trends and business news.'
      },
      cards: []
    },
    {
      id: 'articles',
      visible: true,
      fields: {
        intro: '',
        readLabel: 'Read article'
      },
      cards: []
    },
    {
      id: 'cta',
      visible: true,
      fields: {
        heading: 'Want wholesale pricing on what you just read about?',
        body: 'Our sales desk responds to every quote request within 24 hours.',
        btnLabel: 'Request a quote',
        btnLink: '/quote'
      },
      cards: []
    }
  ]
};

const DEFAULT_POSTS = [
  {
    id: 'post-1',
    title: 'Welcome to Nigson Products',
    slug: 'welcome',
    tag: 'Industry',
    author: 'Nigson Editorial Team',
    date: 'Oct 2026',
    status: 'Published',
    excerpt: 'Welcome to our new store.',
    image: '/assets/blog-market.jpg',
    body: `Welcome to Nigson Products. We are excited to announce the launch of our new official online store and distributor hub, bringing Nigeria's premier technology accessories, power solutions, and commercial products directly to retailers and consumers nationwide.

### What We Offer
- Authentic, high-grade mobile & computing accessories
- Enterprise power protection & rapid GaN charging units
- Dedicated B2B wholesale pricing with nationwide delivery
- Comprehensive manufacturer warranties on all inventory

### Partner With Nigson
Whether you operate a retail shop or oversee corporate procurement, our dedicated sales desk is ready to support your business with competitive margins and dependable supply.`,
    sections: [
      {
        paragraphs: [
          "Welcome to Nigson Products. We are excited to announce the launch of our new official online store and distributor hub, bringing Nigeria's premier technology accessories, power solutions, and commercial products directly to retailers and consumers nationwide."
        ]
      },
      {
        heading: "What We Offer",
        bullets: [
          "Authentic, high-grade mobile & computing accessories",
          "Enterprise power protection & rapid GaN charging units",
          "Dedicated B2B wholesale pricing with nationwide delivery",
          "Comprehensive manufacturer warranties on all inventory"
        ]
      },
      {
        heading: "Partner With Nigson",
        paragraphs: [
          "Whether you operate a retail shop or oversee corporate procurement, our dedicated sales desk is ready to support your business with competitive margins and dependable supply."
        ]
      }
    ]
  },
  {
    id: 'post-2',
    title: 'Launching the GaN 100W adapter across Nigeria',
    slug: 'launching-gan-100w',
    tag: 'Product Update',
    author: 'Nigson Product Team',
    date: 'Jun 2026',
    status: 'Published',
    excerpt: 'Our new 4-port GaN charger is now available to distributors nationwide.',
    image: '/assets/blog-gan.jpg',
    body: `Nigson is proud to announce the nationwide rollout of our flagship GaN 100W four-port desktop adapter — engineered for power users, small offices and busy homes across Nigeria.

### Why GaN matters
GaN (Gallium Nitride) technology delivers up to 3× more efficiency than conventional silicon chargers, in a footprint less than half the size. That means faster charging, lower heat, and a single wall socket that can power a laptop, two phones and a pair of earbuds simultaneously.

### What ships in the box
- Universal Nigerian pin compatibility
- Over-current and over-voltage protection
- Four ports — 3× USB-C, 1× USB-A
- Nigson standard 12-month warranty

### Availability
Retailers report strong early demand — particularly from remote-work customers and university students. Stock is available now from our Lagos, Aba and Kano hubs.

Distributors can request bulk pricing via our Quote form or the WhatsApp sales desk.`,
    sections: [
      {
        paragraphs: [
          "Nigson is proud to announce the nationwide rollout of our flagship GaN 100W four-port desktop adapter — engineered for power users, small offices and busy homes across Nigeria."
        ]
      },
      {
        heading: "Why GaN matters",
        paragraphs: [
          "GaN (Gallium Nitride) technology delivers up to 3× more efficiency than conventional silicon chargers, in a footprint less than half the size. That means faster charging, lower heat, and a single wall socket that can power a laptop, two phones and a pair of earbuds simultaneously."
        ]
      },
      {
        heading: "What ships in the box",
        bullets: [
          "Universal Nigerian pin compatibility",
          "Over-current and over-voltage protection",
          "Four ports — 3× USB-C, 1× USB-A",
          "Nigson standard 12-month warranty"
        ]
      },
      {
        heading: "Availability",
        paragraphs: [
          "Retailers report strong early demand — particularly from remote-work customers and university students. Stock is available now from our Lagos, Aba and Kano hubs.",
          "Distributors can request bulk pricing via our Quote form or the WhatsApp sales desk."
        ]
      }
    ]
  },
  {
    id: 'post-3',
    title: 'The 2026 phone accessories market outlook',
    slug: '2026-market-outlook',
    tag: 'Industry',
    author: 'Nigson Insights Desk',
    date: 'May 2026',
    status: 'Published',
    excerpt: "Consumer demand is shifting — here's how retailers should plan inventory.",
    image: '/assets/blog-market.jpg',
    body: `Nigeria's phone accessories market is projected to grow 14% in 2026, driven by rising smartphone penetration and the migration to USB-C across nearly every device category.

### Three shifts that matter this year
- USB-C is now the dominant connector across new phones — stock accordingly.
- Power banks above 20,000mAh outsell smaller units 3:1 as grid reliability remains uneven.
- TWS earbuds keep displacing wired sets, especially in the 15,000–30,000 NGN band.

### Planning with Nigson
Distributors partnering with Nigson benefit from advance sight of import cycles and category forecasts — allowing them to align stock and cash flow with demand curves rather than react to them.

Talk to your account manager about the 2026 planning pack, or request one directly from our sales team.`,
    sections: [
      {
        paragraphs: [
          "Nigeria's phone accessories market is projected to grow 14% in 2026, driven by rising smartphone penetration and the migration to USB-C across nearly every device category."
        ]
      },
      {
        heading: "Three shifts that matter this year",
        bullets: [
          "USB-C is now the dominant connector across new phones — stock accordingly.",
          "Power banks above 20,000mAh outsell smaller units 3:1 as grid reliability remains uneven.",
          "TWS earbuds keep displacing wired sets, especially in the 15,000–30,000 NGN band."
        ]
      },
      {
        heading: "Planning with Nigson",
        paragraphs: [
          "Distributors partnering with Nigson benefit from advance sight of import cycles and category forecasts — allowing them to align stock and cash flow with demand curves rather than react to them.",
          "Talk to your account manager about the 2026 planning pack, or request one directly from our sales team."
        ]
      }
    ]
  },
  {
    id: 'post-4',
    title: 'Nigson Properties breaks ground on Periwinkle',
    slug: 'periwinkle-groundbreaking',
    tag: 'Announcement',
    author: 'Nigson Communications',
    date: 'Apr 2026',
    status: 'Published',
    excerpt: '9-unit mixed residential development kicks off in Lekki.',
    image: '/assets/blog-groundbreak.jpg',
    body: `Nigson Properties Limited has officially broken ground on Periwinkle — a 9-unit mixed residential development in Lekki, comprising eight 3-bedroom units and a signature 4-bedroom pent-floor with BQ.

### What we are building
- Eight 3-bedroom units and one 4-bedroom pent-floor with BQ
- Premium finishes and smart-home readiness
- Dedicated solar and inverter power
- Generous shared amenities

### Timeline and next steps
Construction is scheduled for 18 months, with a target handover of Q4 2027. Early buyer registrations are open — units at ground-breaking pricing are available on a first-come basis.

Contact our Properties team to arrange a private walkthrough of the plans and pricing.`,
    sections: [
      {
        paragraphs: [
          "Nigson Properties Limited has officially broken ground on Periwinkle — a 9-unit mixed residential development in Lekki, comprising eight 3-bedroom units and a signature 4-bedroom pent-floor with BQ."
        ]
      },
      {
        heading: "What we are building",
        bullets: [
          "Eight 3-bedroom units and one 4-bedroom pent-floor with BQ",
          "Premium finishes and smart-home readiness",
          "Dedicated solar and inverter power",
          "Generous shared amenities"
        ]
      },
      {
        heading: "Timeline and next steps",
        paragraphs: [
          "Construction is scheduled for 18 months, with a target handover of Q4 2027. Early buyer registrations are open — units at ground-breaking pricing are available on a first-come basis.",
          "Contact our Properties team to arrange a private walkthrough of the plans and pricing."
        ]
      }
    ]
  },
  {
    id: 'post-5',
    title: 'How to spot counterfeit power banks',
    slug: 'spot-counterfeit-power-banks',
    tag: 'Guide',
    author: 'Nigson Quality Assurance',
    date: 'Mar 2026',
    status: 'Published',
    excerpt: 'A quick checklist for retailers to protect their customers.',
    image: '/assets/blog-counterfeit.jpg',
    body: `Counterfeit power banks aren't just a margin problem — they're a safety hazard. Here's the quick checklist we train every Nigson retailer to run before accepting stock.

### The four-point checklist
- Weight test: a 20,000mAh unit under 350g is almost certainly under-rated or filled with sand/paper.
- Charge cycle: a genuine 10,000mAh unit should recharge a 4,000mAh phone at least twice.
- Print quality: crisp printing, aligned USB ports, matched serial numbers on box, unit and certificate.
- Certification: SONCAP, CE and RoHS marks, plus a scannable QR authenticity code.

### When in doubt
Buy from an authorised Nigson distributor. Our supply chain guarantees genuine cells, tested to spec, every cycle.`,
    sections: [
      {
        paragraphs: [
          "Counterfeit power banks aren't just a margin problem — they're a safety hazard. Here's the quick checklist we train every Nigson retailer to run before accepting stock."
        ]
      },
      {
        heading: "The four-point checklist",
        bullets: [
          "Weight test: a 20,000mAh unit under 350g is almost certainly under-rated or filled with sand/paper.",
          "Charge cycle: a genuine 10,000mAh unit should recharge a 4,000mAh phone at least twice.",
          "Print quality: crisp printing, aligned USB ports, matched serial numbers on box, unit and certificate.",
          "Certification: SONCAP, CE and RoHS marks, plus a scannable QR authenticity code."
        ]
      },
      {
        heading: "When in doubt",
        paragraphs: [
          "Buy from an authorised Nigson distributor. Our supply chain guarantees genuine cells, tested to spec, every cycle."
        ]
      }
    ]
  }
];

const sql = `
  INSERT INTO experience.admin_cms_store (key, data, updated_at)
  VALUES 
    ('page_insights', '${JSON.stringify(INSIGHTS_PAGE).replace(/'/g, "''")}'::jsonb, now()),
    ('blog_posts', '${JSON.stringify(DEFAULT_POSTS).replace(/'/g, "''")}'::jsonb, now())
  ON CONFLICT (key) DO UPDATE SET data = EXCLUDED.data, updated_at = now();

  UPDATE experience.content_revision
  SET body = '${JSON.stringify({
    schemaVersion: 1,
    blocks: INSIGHTS_PAGE.sections.map(s => ({
      type: 'TEXT',
      text: JSON.stringify({ id: s.id, visible: s.visible, fields: s.fields, cards: s.cards })
    }))
  }).replace(/'/g, "''")}'::jsonb,
  title = 'Insights',
  updated_at = now()
  WHERE content_item_id IN (
    SELECT id FROM experience.content_item WHERE slug = 'insights'
  );
`;

cp.execFileSync('docker', ['exec', '-i', 'nigson-local-postgres-1', 'psql', '-U', 'nigson_bootstrap', '-d', 'nigson', '-c', sql], { encoding: 'utf8' });
console.log('Successfully seeded page_insights and blog_posts in PostgreSQL database!');

