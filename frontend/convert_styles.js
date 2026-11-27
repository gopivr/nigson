const fs = require('fs');

const stylesPath = 'C:\\Techxplorers\\nigson-proj\\Nigson-micro-frontend\\projects\\shell\\src\\styles.css';
let content = fs.readFileSync(stylesPath, 'utf8');

// Replace @import "tailwindcss" with v3 directives
content = content.replace(/@import "tailwindcss";/g, '@tailwind base;\n@tailwind components;\n@tailwind utilities;');
content = content.replace(/@source.*?;/g, '');

// Remove @theme inline block
content = content.replace(/@theme inline\s*\{[^}]+\}/g, '');

// Convert @utility to @layer utilities { .utility-name { ... } }
content = content.replace(/@utility ([\w-]+)\s*\{([^}]+)\}/g, '@layer utilities {\n  .$1 {\n    $2\n  }\n}');

fs.writeFileSync(stylesPath, content);
console.log('Converted styles.css to Tailwind v3 syntax');
