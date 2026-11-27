import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'lib-badge',
  standalone: true,
  imports: [CommonModule],
  template: `
    <span [class]="'inline-block rounded-full px-2.5 py-0.5 text-[10px] font-semibold ' + getToneClass()">
      <ng-content></ng-content>
    </span>
  `
})
export class BadgeComponent {
  @Input() tone: 'neutral' | 'success' | 'warning' | 'danger' | 'info' = 'neutral';
  
  getToneClass() {
    switch (this.tone) {
      case 'success': return 'bg-emerald-100 text-emerald-700';
      case 'warning': return 'bg-amber-100 text-amber-700';
      case 'danger': return 'bg-rose-100 text-rose-700';
      case 'info': return 'bg-blue-100 text-blue-700';
      default: return 'bg-surface-alt text-ink';
    }
  }
}

export function statusTone(status: string): 'neutral' | 'success' | 'warning' | 'danger' | 'info' {
  switch (status?.toLowerCase()) {
    case "active":
    case "published":
    case "delivered":
    case "approved":
    case "resolved":
    case "paid":
      return "success";
    case "pending":
    case "under review":
    case "processing":
      return "warning";
    case "cancelled":
    case "rejected":
    case "suspended":
    case "disabled":
    case "out of stock":
      return "danger";
    case "shipped":
    case "packed":
    case "in progress":
    case "quoted":
      return "info";
    default:
      return "neutral";
  }
}
