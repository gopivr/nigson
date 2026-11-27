import { Component, Input, HostBinding, computed } from '@angular/core';
import { cn } from '../utils';

@Component({
  selector: 'input[ui-input], textarea[ui-input]',
  standalone: true,
  template: '',
  host: {
    '[class]': 'computedClass()'
  }
})
export class InputComponent {
  @Input() class: string = '';

  computedClass = computed(() => {
    return cn(
      "flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-base shadow-sm transition-colors file:border-0 file:bg-transparent file:text-sm file:font-medium file:text-foreground placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50 md:text-sm",
      this.class
    );
  });
}
