import {
  Component,
  ElementRef,
  HostListener,
  computed,
  effect,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  ActivatedRoute,
  NavigationEnd,
  Router,
  RouterLink,
  RouterLinkActive,
  RouterOutlet,
} from '@angular/router';
import { filter } from 'rxjs';

import {
  APP_ROLES,
  BREADCRUMB_DATA_KEY,
  NAVIGATION_GROUPS,
  NAVIGATION_SECTIONS,
  ROLE_LABELS,
  canAccessSection,
  isAppRole,
} from '../../core/navigation/app-navigation';
import { DemoSessionService } from '../../core/session/demo-session.service';

interface Breadcrumb {
  readonly label: string;
  readonly url: string;
}

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './app-shell.html',
  styleUrl: './app-shell.scss',
})
export class AppShell {
  private readonly activatedRoute = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly session = inject(DemoSessionService);
  protected readonly roles = APP_ROLES;
  protected readonly roleLabels = ROLE_LABELS;
  protected readonly menuOpen = signal(false);

  private readonly menuButton = viewChild<ElementRef<HTMLButtonElement>>('menuButton');
  private readonly firstNavigationLink =
    viewChild<ElementRef<HTMLAnchorElement>>('firstNavigationLink');
  private readonly mainContent = viewChild<ElementRef<HTMLElement>>('mainContent');
  private readonly lastNavigation = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
    ),
    { initialValue: null },
  );

  protected readonly visibleGroups = computed(() =>
    NAVIGATION_GROUPS.map((group) => ({
      ...group,
      sections: NAVIGATION_SECTIONS.filter(
        (section) => section.group === group.id && canAccessSection(this.session.role(), section),
      ),
    })).filter((group) => group.sections.length > 0),
  );

  protected readonly breadcrumbs = computed(() => {
    this.lastNavigation();
    return this.collectBreadcrumbs();
  });

  private readonly focusRouteHeading = effect(() => {
    const navigation = this.lastNavigation();

    if (navigation) {
      queueMicrotask(() => {
        this.mainContent()?.nativeElement.querySelector<HTMLElement>('h1')?.focus();
      });
    }
  });

  protected toggleMenu(): void {
    const willOpen = !this.menuOpen();
    this.menuOpen.set(willOpen);

    if (willOpen) {
      queueMicrotask(() => this.firstNavigationLink()?.nativeElement.focus());
    }
  }

  protected closeMenu(): void {
    this.menuOpen.set(false);
  }

  protected changeRole(event: Event): void {
    const role = (event.target as HTMLSelectElement).value;

    if (isAppRole(role)) {
      this.session.setRole(role);
      this.closeMenu();
      void this.router.navigate(['/dashboard']);
    }
  }

  @HostListener('document:keydown.escape')
  protected closeMenuWithKeyboard(): void {
    if (this.menuOpen()) {
      this.closeMenu();
      this.menuButton()?.nativeElement.focus();
    }
  }

  private collectBreadcrumbs(): Breadcrumb[] {
    const breadcrumbs: Breadcrumb[] = [];
    let currentRoute: ActivatedRoute | null = this.activatedRoute;
    let url = '';

    while (currentRoute?.firstChild) {
      currentRoute = currentRoute.firstChild;
      const routePath = currentRoute.snapshot.url.map((segment) => segment.path).join('/');

      if (routePath) {
        url += `/${routePath}`;
      }

      const label = currentRoute.snapshot.data[BREADCRUMB_DATA_KEY] as string | undefined;
      if (label) {
        breadcrumbs.push({ label, url: url || '/dashboard' });
      }
    }

    return breadcrumbs;
  }
}
