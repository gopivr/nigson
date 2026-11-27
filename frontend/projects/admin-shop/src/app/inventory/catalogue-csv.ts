export const DESCRIPTION_COLUMNS=['overview','features','highlights','warranty','connectors','power_rating','compatibility','build_material','certification','importer','packaging','carton_specs','warehouse_status','dispatch_turnaround','nationwide_shipping','min_wholesale_order','price_subtitle'] as const;
export const PACKAGE_COLUMNS=['maximum_units_per_package','unit_weight_kg','tare_weight_kg','length_cm','width_cm','height_cm'] as const;
/** The everyday template deliberately excludes metadata/JSON fields. */
export const BASIC_CSV_COLUMNS=['sku','name','category','description','retail_price','wholesale_price','wholesale_minimum','distributor_price','distributor_minimum','unit','pack_size',...PACKAGE_COLUMNS] as const;
/** The advanced template exposes every supported importer field. */
export const CSV_COLUMNS=['sku','name','slug','category','description','retail_price','wholesale_price','wholesale_minimum','distributor_price','distributor_minimum','unit','quantity_scale','pack_size','division_code',...PACKAGE_COLUMNS,...DESCRIPTION_COLUMNS,'specifications_json'] as const;
export type CsvRecord=Record<string,string>;
// RFC-style comma-separated cells, including escaped quotes and multiline descriptions.
export function parseCatalogueCsv(text:string):CsvRecord[]{
 if(text.length>2_000_000)throw Error('Use a CSV smaller than 2 MB.');
 text=text.replace(/^\uFEFF/,'');const records:string[][]=[];let row:string[]=[],cell='',quoted=false,closed=false;
 const finishCell=()=>{row.push(cell);cell='';closed=false;};
 const finishRow=()=>{finishCell();if(row.some(c=>c.trim()))records.push(row);row=[];};
 for(let i=0;i<text.length;i++){const c=text[i];if(quoted){if(c==='"'){if(text[i+1]==='"'){cell+='"';i++;}else{quoted=false;closed=true;}}else cell+=c;continue;}
  if(closed&&c!==','&&c!=='\r'&&c!=='\n')throw Error('Unexpected text after a quoted CSV cell.');
  if(c==='"'){if(cell)throw Error('Quotes must surround the entire CSV cell.');quoted=true;}
  else if(c===',')finishCell();else if(c==='\n'||c==='\r'){if(c==='\r'&&text[i+1]==='\n')i++;finishRow();}else cell+=c;
 }
 if(quoted)throw Error('A quoted CSV cell is not closed.');if(cell||row.length||closed)finishRow();
 const headers=records.shift()?.map(h=>h.trim());if(!headers?.includes('sku'))throw Error('The CSV must include the sku column.');
 if(new Set(headers).size!==headers.length)throw Error('Duplicate column headings are not allowed.');
 const unknown=headers.filter(h=>!(CSV_COLUMNS as readonly string[]).includes(h));if(unknown.length)throw Error('Unknown columns: '+unknown.join(', '));
 if(!records.length||records.length>50)throw Error('Upload between 1 and 50 product rows per file.');
 const seen=new Set<string>();return records.map((cells,index)=>{if(cells.length!==headers.length)throw Error('Row '+(index+2)+' has the wrong number of cells.');const value=Object.fromEntries(headers.map((h,i)=>[h,cells[i].trim()]));if(!value['sku']||value['sku'].length>80)throw Error('Row '+(index+2)+' needs a SKU of at most 80 characters.');const key=value['sku'].toLowerCase();if(seen.has(key))throw Error('Duplicate SKU in file: '+value['sku']);seen.add(key);return value;});
}
export function csvTemplate(columns:ReadonlyArray<string>=CSV_COLUMNS){return '\uFEFF'+columns.join(',')+'\r\n';}
export function specificationPatch(row:CsvRecord){const specs:Record<string,unknown>={};if(row['specifications_json']){let parsed;try{parsed=JSON.parse(row['specifications_json']);}catch{throw Error('specifications_json must be a valid JSON object.');}if(!parsed||Array.isArray(parsed)||typeof parsed!=='object')throw Error('specifications_json must be a JSON object.');Object.assign(specs,parsed);}
 for(const field of DESCRIPTION_COLUMNS)if(row[field])specs[field]=['features','highlights','packaging','carton_specs'].includes(field)?row[field].split('|').map(v=>v.trim()).filter(Boolean):row[field];
 if(Object.keys(specs).length>50)throw Error('Use at most 50 specifications.');
 for(const [key,value] of Object.entries(specs)){if(['starting_price','wholesale_rate','distributor_rate'].includes(key)||!/^[a-z][a-z0-9_]{0,79}$/.test(key))throw Error('Unsupported specification key: '+key);if(!(typeof value==='boolean'||typeof value==='string'&&value.length<=1000||Array.isArray(value)&&value.length<=20&&value.every(v=>typeof v==='string'&&v.length<=1000)))throw Error('Specifications must be text, booleans or lists of text (maximum 1000 characters per value).');}
 return specs;
}
