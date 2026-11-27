import { normalizePhoneNumber } from './phone-number';
import { Component, Input, forwardRef } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
@Component({
 selector:'ui-phone-input',standalone:true,
 providers:[{provide:NG_VALUE_ACCESSOR,useExisting:forwardRef(()=>PhoneInputComponent),multi:true}],
 template:`
  <div class="mt-1.5 flex overflow-hidden rounded-xl border border-hairline bg-white focus-within:border-brand">
   <select aria-label="Phone country code" [value]="mode" (change)="changeMode($any($event.target).value)" (blur)="touch()" [disabled]="disabled" class="w-36 shrink-0 border-r border-hairline bg-surface-alt px-2 py-2.5 text-sm focus:outline-none disabled:opacity-60">
    <option value="NG">Nigeria (+234)</option><option value="international">International</option>
   </select>
   <input type="tel" inputmode="tel" aria-label="Phone number" [attr.autocomplete]="mode==='NG'?'tel-national':'tel'" [value]="number" (input)="input($any($event.target).value)" (blur)="touch()" [disabled]="disabled" [placeholder]="mode==='NG'?'8012345678':'+ country code and number'" class="min-w-0 flex-1 bg-transparent px-3 py-2.5 text-sm focus:outline-none disabled:bg-surface-alt" />
  </div>`
})
export class PhoneInputComponent implements ControlValueAccessor {
 @Input() disabled=false;
 mode:'NG'|'international'='NG';
 number='';
 private onChange: (value:string)=>void=()=>{};
 touch:()=>void=()=>{};
 writeValue(value:string|null):void {
  let raw=normalizePhoneNumber(value) ?? (value??'').trim();
  if (/^234[1-9][0-9]{9}$/.test(raw)) raw='+'+raw;
  this.mode=raw.startsWith('+')&&!raw.startsWith('+234')?'international':'NG';
  this.number=this.mode==='NG'?raw.replace(/^\+234/,''):raw;
 }
 input(value:string):void {
  if(this.disabled)return;
  const compact=value.trim().replace(/[\s()-]/g,'');
  if (/^234[1-9][0-9]{9}$/.test(compact)) {this.writeValue('+'+compact);this.onChange('+'+compact);return;}
  if(value.trim().startsWith('+')) {
   this.writeValue(value);this.onChange(value);
  } else {
   this.number=value;
   const raw=value.trim();
   this.onChange(!raw?'':this.mode==='NG'?'+234'+raw.replace(/^0/,''):'+'+raw);
  }
 }
 changeMode(mode:'NG'|'international'):void {
  if(this.disabled)return;
  this.mode=mode;this.number='';this.onChange('');this.touch();
 }
 registerOnChange(fn:(value:string)=>void):void{this.onChange=fn;}
 registerOnTouched(fn:()=>void):void{this.touch=fn;}
 setDisabledState(disabled:boolean):void{this.disabled=disabled;}
}
