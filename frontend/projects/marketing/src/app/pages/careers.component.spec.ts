import '@angular/compiler';
import { Injector, signal } from '@angular/core';
import { of, Subject } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CmsService, MarketingService } from 'shared-ui';
import { CareersComponent } from './careers.component';
import { AttachedCvFile, CareerStorageService } from './career-storage';

function createMockFile(name: string, type: string, size: number): File {
  const content = 'a'.repeat(Math.min(size, 1024));
  const file = new File([content], name, { type });
  Object.defineProperty(file, 'size', { value: size });
  return file;
}

function setup() {
  const submitCareerApplication = vi.fn((_id: string, _payload: any) => of(true));
  const loadCareers = vi.fn(() => of([]));
  const mockPageSections = {
    get: vi.fn((sectionId: string) => ({
      visible: true,
      t: (key: string) => key,
      cards: (mapper: any) => [{ title: 'Sales Executive — Phone Accessories', type: 'Full-time', location: 'Lagos' }].map(mapper)
    }))
  };

  const injector = Injector.create({
    providers: [
      {
        provide: MarketingService,
        useValue: {
          careers: signal([]),
          loadingCareers: signal(false),
          loadCareers,
          submitCareerApplication
        }
      },
      {
        provide: CmsService,
        useValue: {
          getPageSections: () => mockPageSections
        }
      },
      {
        provide: CareersComponent,
        useFactory: () => new CareersComponent()
      }
    ]
  });

  const component = injector.get(CareersComponent);
  component.careerStorage.fileToDataUrl = vi.fn().mockResolvedValue('data:application/pdf;base64,JVBERi0xLjQK...');
  return { component, submitCareerApplication, loadCareers };
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('CareersComponent - CV File Attachment and Storage', () => {
  it('initializes with file attachment mode by default', () => {
    const { component } = setup();
    expect(component.cvMode()).toBe('file');
    expect(component.attachedFile()).toBeNull();
    expect(component.fileError()).toBeNull();
  });

  it('attaches and stores a valid local PDF resume file', async () => {
    const { component } = setup();
    const pdfFile = createMockFile('john_doe_resume.pdf', 'application/pdf', 1024 * 250);

    await component.handleFile(pdfFile);

    expect(component.fileError()).toBeNull();
    const attached = component.attachedFile();
    expect(attached).not.toBeNull();
    expect(attached?.name).toBe('john_doe_resume.pdf');
    expect(attached?.size).toBe(1024 * 250);
    expect(attached?.dataUrl).toContain('data:application/pdf;base64');
  });

  it('rejects invalid file types and displays an informative error', async () => {
    const { component } = setup();
    const exeFile = createMockFile('malicious.exe', 'application/x-msdownload', 1024 * 50);

    await component.handleFile(exeFile);

    expect(component.attachedFile()).toBeNull();
    expect(component.fileError()).toContain('Please select a PDF, DOC, or DOCX document.');
  });

  it('rejects files exceeding 10 MB limit', async () => {
    const { component } = setup();
    const hugePdf = createMockFile('huge_portfolio.pdf', 'application/pdf', 11 * 1024 * 1024);

    await component.handleFile(hugePdf);

    expect(component.attachedFile()).toBeNull();
    expect(component.fileError()).toContain('File exceeds maximum size of 10 MB.');
  });

  it('allows removing an attached file', async () => {
    const { component } = setup();
    const pdfFile = createMockFile('resume.pdf', 'application/pdf', 1024 * 100);
    await component.handleFile(pdfFile);
    expect(component.attachedFile()).not.toBeNull();

    component.removeAttachedFile();
    expect(component.attachedFile()).toBeNull();
  });

  it('validates that either a file or a URL must be provided before submission', () => {
    const { component, submitCareerApplication } = setup();
    component.appForm.name = 'Jane Doe';
    component.appForm.email = 'jane@example.com';
    component.appForm.phone = '+2348012345678';
    component.appForm.cv = '';

    component.submitApplication('Sales Executive — Phone Accessories', 'opp_123');

    expect(submitCareerApplication).not.toHaveBeenCalled();
    expect(component.fileError()).toContain('Please attach your local Resume/CV file or provide a link.');
  });

  it('submits application successfully when a local file is attached and stores reference', async () => {
    const { component, submitCareerApplication } = setup();
    const pdfFile = createMockFile('jane_doe_cv.pdf', 'application/pdf', 1024 * 150);
    await component.handleFile(pdfFile);

    component.appForm.name = 'Jane Doe';
    component.appForm.email = 'jane@example.com';
    component.appForm.phone = '+2348012345678';

    const saveSpy = vi.spyOn(component.careerStorage, 'saveApplication');

    component.submitApplication('Sales Executive — Phone Accessories', 'opp_123');

    expect(submitCareerApplication).toHaveBeenCalledWith('opp_123', expect.objectContaining({
      name: 'Jane Doe',
      email: 'jane@example.com',
      answers: expect.objectContaining({
        positionTitle: 'Sales Executive — Phone Accessories',
        cvUrl: 'stored://jane_doe_cv.pdf'
      })
    }));

    expect(saveSpy).toHaveBeenCalledWith(expect.objectContaining({
      jobTitle: 'Sales Executive — Phone Accessories',
      applicantName: 'Jane Doe',
      cvType: 'file',
      cvUrl: 'stored://jane_doe_cv.pdf'
    }));

    expect(component.appliedTo()).toBe('Sales Executive — Phone Accessories');
    expect(component.lastSubmittedCv()?.name).toBe('jane_doe_cv.pdf');
  });

  it('submits application successfully with a URL when URL mode is chosen', () => {
    const { component, submitCareerApplication } = setup();
    component.cvMode.set('url');
    component.appForm.name = 'Alex Hunter';
    component.appForm.email = 'alex@example.com';
    component.appForm.phone = '+2348098765432';
    component.appForm.cv = 'https://drive.google.com/file/d/alex-resume/view';

    component.submitApplication('Sales Executive — Phone Accessories', 'opp_123');

    expect(submitCareerApplication).toHaveBeenCalledWith('opp_123', expect.objectContaining({
      name: 'Alex Hunter',
      email: 'alex@example.com',
      answers: expect.objectContaining({
        cvUrl: 'https://drive.google.com/file/d/alex-resume/view'
      })
    }));

    expect(component.appliedTo()).toBe('Sales Executive — Phone Accessories');
  });
});
