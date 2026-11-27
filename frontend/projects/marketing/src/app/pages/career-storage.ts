export interface AttachedCvFile {
  id: string;
  name: string;
  size: number;
  type: string;
  dataUrl: string;
  lastModified?: number;
  storedAt: string;
}

export interface StoredCareerApplication {
  id: string;
  opportunityId: string;
  jobTitle: string;
  applicantName: string;
  applicantEmail: string;
  applicantPhone: string;
  coverLetter?: string;
  cvType: 'file' | 'url';
  cvUrl?: string;
  cvFile?: AttachedCvFile;
  submittedAt: string;
}

export class CareerStorageService {
  private readonly dbName = 'nigson_career_storage';
  private readonly storeName = 'attached_cvs';
  private readonly appsKey = 'nigson_career_applications';
  private memoryFiles = new Map<string, AttachedCvFile>();

  /** Convert File to Base64 Data URL */
  fileToDataUrl(file: File): Promise<string> {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(reader.result as string);
      reader.onerror = () => reject(new Error('Failed to read local file'));
      reader.readAsDataURL(file);
    });
  }

  /** Open IndexedDB database */
  private async getDb(): Promise<IDBDatabase | null> {
    if (typeof window === 'undefined' || !window.indexedDB) {
      return null;
    }
    return new Promise((resolve) => {
      try {
        const req = window.indexedDB.open(this.dbName, 1);
        req.onupgradeneeded = () => {
          const db = req.result;
          if (!db.objectStoreNames.contains(this.storeName)) {
            db.createObjectStore(this.storeName, { keyPath: 'id' });
          }
        };
        req.onsuccess = () => resolve(req.result);
        req.onerror = () => resolve(null);
      } catch {
        resolve(null);
      }
    });
  }

  /** Store an attached CV file into IndexedDB & memory */
  async storeCvFile(file: File): Promise<AttachedCvFile> {
    const dataUrl = await this.fileToDataUrl(file);
    const cvFile: AttachedCvFile = {
      id: 'cv_' + Date.now() + '_' + Math.random().toString(36).substring(2, 9),
      name: file.name,
      size: file.size,
      type: file.type || 'application/octet-stream',
      dataUrl,
      lastModified: file.lastModified,
      storedAt: new Date().toISOString()
    };

    this.memoryFiles.set(cvFile.id, cvFile);

    const db = await this.getDb();
    if (db) {
      await new Promise<void>((resolve) => {
        try {
          const tx = db.transaction(this.storeName, 'readwrite');
          tx.objectStore(this.storeName).put(cvFile);
          tx.oncomplete = () => resolve();
          tx.onerror = () => resolve();
        } catch {
          resolve();
        }
      });
    }

    return cvFile;
  }

  /** Retrieve an attached CV file by id */
  async getStoredCvFile(id: string): Promise<AttachedCvFile | null> {
    if (this.memoryFiles.has(id)) {
      return this.memoryFiles.get(id)!;
    }
    const db = await this.getDb();
    if (!db) return null;
    return new Promise((resolve) => {
      try {
        const tx = db.transaction(this.storeName, 'readonly');
        const req = tx.objectStore(this.storeName).get(id);
        req.onsuccess = () => resolve(req.result || null);
        req.onerror = () => resolve(null);
      } catch {
        resolve(null);
      }
    });
  }

  /** Save application metadata to localStorage index */
  saveApplication(app: StoredCareerApplication): void {
    try {
      if (typeof window !== 'undefined' && window.localStorage) {
        const existingRaw = window.localStorage.getItem(this.appsKey);
        const list: StoredCareerApplication[] = existingRaw ? JSON.parse(existingRaw) : [];
        const compactApp: StoredCareerApplication = {
          ...app,
          cvFile: app.cvFile ? {
            ...app.cvFile,
            dataUrl: '' // Omit full base64 data in localStorage to prevent quota limit issues
          } : undefined
        };
        list.unshift(compactApp);
        window.localStorage.setItem(this.appsKey, JSON.stringify(list.slice(0, 50)));
      }
    } catch (e) {
      console.warn('[CareerStorageService] Failed to update localStorage index:', e);
    }
  }

  /** Download an attached CV file to the user's computer */
  downloadFile(cvFile: AttachedCvFile): void {
    if (typeof window === 'undefined') return;
    try {
      const link = document.createElement('a');
      link.href = cvFile.dataUrl;
      link.download = cvFile.name;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
    } catch (e) {
      console.error('[CareerStorageService] Download error:', e);
    }
  }
}
