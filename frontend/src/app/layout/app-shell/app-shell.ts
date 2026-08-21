import {
  Component,
  ElementRef,
  HostListener,
  Injector,
  afterNextRender,
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
import { filter, finalize } from 'rxjs';

import {
  BREADCRUMB_DATA_KEY,
  NAVIGATION_GROUPS,
  NAVIGATION_SECTIONS,
  ROLE_LABELS,
  canAccessSection,
} from '../../core/navigation/app-navigation';
import { SessionService } from '../../core/session/session.service';

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
  private readonly injector = inject(Injector);
  private readonly router = inject(Router);

  protected readonly session = inject(SessionService);
  protected readonly menuOpen = signal(false);
  protected readonly loggingOut = signal(false);
  protected readonly displayName = computed(
    () => this.session.user()?.username ?? this.session.user()?.email ?? 'Usuario',
  );
  protected readonly roleSummary = computed(() =>
    this.session
      .roles()
      .map((role) => ROLE_LABELS[role])
      .join(', '),
  );

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
        (section) => section.group === group.id && canAccessSection(this.session.roles(), section),
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
      afterNextRender(
        () => {
          requestAnimationFrame(() => this.firstNavigationLink()?.nativeElement.focus());
        },
        { injector: this.injector },
      );
    }
  }

  protected closeMenu(): void {
    this.menuOpen.set(false);
  }

  protected logout(): void {
    if (this.loggingOut()) {
      return;
    }

    this.loggingOut.set(true);
    this.session
      .logout()
      .pipe(finalize(() => this.loggingOut.set(false)))
      .subscribe(() => void this.router.navigate(['/login']));
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
      if (label && breadcrumbs.at(-1)?.url !== (url || '/dashboard')) {
        breadcrumbs.push({ label, url: url || '/dashboard' });
      }
    }

    return breadcrumbs;
  }
}
