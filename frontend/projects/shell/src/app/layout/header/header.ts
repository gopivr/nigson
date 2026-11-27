import { Component, HostListener, computed, signal, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, Router } from '@angular/router';
import { LucideMenu, LucideX, LucideChevronRight, LucideShoppingBag, LucideUser, LucideUserRound, LucidePackage, LucideLogOut, LucideSearch } from '@lucide/angular';
import { ShopService, SessionService } from 'shared-ui';
import { NavSearchComponent } from './nav-search.component';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule, RouterLink, LucideMenu, LucideX, LucideChevronRight, LucideShoppingBag, LucideUser, LucideUserRound, LucidePackage, LucideLogOut, LucideSearch, NavSearchComponent],
  templateUrl: './header.html',
  styleUrls: ['./header.css']
})
export class HeaderComponent {
  open = signal(false);
  megaOpen = signal(false);
  profileOpen = signal(false);
  searchOpen = signal(false);
  scrolled = signal(false);

  constructor(public shop: ShopService, private router: Router, public session: SessionService) {}

  @HostListener('window:scroll', [])
  onWindowScroll() {
    this.scrolled.set(window.scrollY > 20);
  }

  @HostListener('document:mousedown', ['$event'])
  onDocumentClick(event: MouseEvent) {
    if (this.profileOpen()) {
      const target = event.target as HTMLElement;
      if (!target.closest('.profile-dropdown-container')) {
        this.profileOpen.set(false);
      }
    }
  }

  toggleOpen() {
    this.open.update(v => !v);
  }

  toggleProfileOpen() {
    this.profileOpen.update(v => !v);
  }

  handleLogout() {
    this.shop.signOut().subscribe(() => {
      this.profileOpen.set(false);
      this.router.navigate(['/']);
    });
  }

  handleSearch(term: string) {
    const query = term.trim();
    if (query) {
      this.router.navigate(['/search'], { queryParams: { q: query } });
    }
  }
  /** Deterministic avatar background from user's name initials (replaces avatarColor field) */
  avatarBg(): string {
    const name = this.shop.user()?.fullName ?? '';
    const colors = ['#10B981','#2563EB','#7C3AED','#DB2777','#D97706','#059669'];
    let h = 0;
    for (let i = 0; i < name.length; i++) h = (h * 31 + name.charCodeAt(i)) >>> 0;
    return colors[h % colors.length];
  }
}
