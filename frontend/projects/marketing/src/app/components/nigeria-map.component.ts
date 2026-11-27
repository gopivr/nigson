import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-nigeria-map',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="relative aspect-[5/4] w-full">
      <svg viewBox="0 0 520 420" class="w-full h-full">
        <!-- Stylized Nigeria outline -->
        <path
          d="M 90,110 Q 70,80 120,70 L 200,55 Q 260,50 320,70 L 420,90 Q 470,110 465,160 L 460,220 Q 455,270 425,290 L 380,340 Q 340,380 280,385 L 220,390 Q 170,385 140,360 L 100,320 Q 70,290 75,240 L 80,180 Q 82,140 90,110 Z"
          fill="#fafafa"
          stroke="#ececec"
          stroke-width="1.5"
        />
        <!-- Location dots -->
        <g *ngFor="let loc of locations; let i = index" [style.animation]="'fadeIn 0.6s ease-out ' + (i * 0.1) + 's both'">
          <circle [attr.cx]="loc.x" [attr.cy]="loc.y" [attr.r]="loc.size + 10" fill="#d71920" opacity="0.12">
            <animate attributeName="r" [attr.values]="(loc.size + 4) + ';' + (loc.size + 14) + ';' + (loc.size + 4)" dur="2.6s" [attr.begin]="(i * 0.15) + 's'" repeatCount="indefinite" />
            <animate attributeName="opacity" values="0.2;0;0.2" dur="2.6s" [attr.begin]="(i * 0.15) + 's'" repeatCount="indefinite" />
          </circle>
          <circle [attr.cx]="loc.x" [attr.cy]="loc.y" [attr.r]="loc.size / 2 + 1" fill="#d71920" />
          <text [attr.x]="loc.x + loc.size + 4" [attr.y]="loc.y + 3" font-size="11" font-weight="600" fill="#111111">
            {{ loc.name }}
          </text>
        </g>
      </svg>
    </div>
  `
})
export class NigeriaMapComponent {
  locations = [
    { name: "Lagos", x: 120, y: 320, size: 8 },
    { name: "Abuja", x: 280, y: 210, size: 7 },
    { name: "Kano", x: 305, y: 100, size: 6 },
    { name: "Port Harcourt", x: 240, y: 360, size: 6 },
    { name: "Ibadan", x: 155, y: 290, size: 5 },
    { name: "Onitsha", x: 230, y: 300, size: 5 },
    { name: "Kaduna", x: 265, y: 155, size: 5 },
    { name: "Enugu", x: 260, y: 285, size: 4 },
    { name: "Benin", x: 190, y: 310, size: 4 },
    { name: "Sokoto", x: 175, y: 80, size: 4 },
    { name: "Maiduguri", x: 435, y: 130, size: 4 },
    { name: "Calabar", x: 305, y: 355, size: 4 },
  ];
}
