import { Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { APP_SECTION_DATA_KEY, AppSection } from '../../core/navigation/app-navigation';

@Component({
  selector: 'app-section-placeholder',
  standalone: true,
  templateUrl: './section-placeholder.html',
  styleUrl: './section-placeholder.scss',
})
export class SectionPlaceholder {
  private readonly route = inject(ActivatedRoute);

  protected readonly section = this.route.snapshot.data[APP_SECTION_DATA_KEY] as AppSection;
}
