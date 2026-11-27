import { Component, Input, Output, EventEmitter, ViewChildren, QueryList, ElementRef, AfterViewInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'ui-otp-input',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="flex gap-2 sm:gap-3">
      <input
        *ngFor="let _ of getArray(); let i = index"
        #otpInput
        inputmode="numeric"
        maxlength="1"
        [attr.aria-label]="'Digit ' + (i + 1)"
        [value]="value[i] || ''"
        (input)="onInput($event, i)"
        (keydown)="onKeyDown($event, i)"
        (paste)="onPaste($event)"
        class="w-full max-w-[56px] flex-1 rounded-xl border bg-white py-3.5 text-center text-lg font-bold outline-none transition-all focus:border-brand focus:ring-2 focus:ring-brand/15"
        [ngClass]="invalid ? 'border-brand text-brand' : 'border-hairline text-ink'"
      />
    </div>
  `
})
export class OtpInputComponent implements AfterViewInit {
  @Input() value: string = '';
  @Output() valueChange = new EventEmitter<string>();
  
  @Input() length = 6;
  @Input() invalid = false;

  @ViewChildren('otpInput') inputs!: QueryList<ElementRef<HTMLInputElement>>;

  getArray() {
    return new Array(this.length);
  }

  ngAfterViewInit() {
    setTimeout(() => {
      this.inputs.first?.nativeElement.focus();
    }, 10);
  }

  onInput(e: Event, index: number) {
    const input = e.target as HTMLInputElement;
    const d = input.value.replace(/\D/g, "").slice(-1);
    
    let nextValue = (this.value.slice(0, index) + d + this.value.slice(index + 1)).slice(0, this.length);
    this.valueChange.emit(nextValue);

    if (d && index < this.length - 1) {
      this.inputs.toArray()[index + 1].nativeElement.focus();
    }
  }

  onKeyDown(e: KeyboardEvent, index: number) {
    if (e.key === 'Backspace' && !this.value[index] && index > 0) {
      this.inputs.toArray()[index - 1].nativeElement.focus();
    }
  }

  onPaste(e: ClipboardEvent) {
    e.preventDefault();
    const text = e.clipboardData?.getData("text").replace(/\D/g, "").slice(0, this.length) || '';
    if (text) {
      this.valueChange.emit(text);
      const focusIndex = Math.min(text.length, this.length - 1);
      setTimeout(() => {
        this.inputs.toArray()[focusIndex]?.nativeElement.focus();
      }, 0);
    }
  }
}
