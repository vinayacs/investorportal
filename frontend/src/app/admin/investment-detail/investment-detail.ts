import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink, RouterLinkActive } from '@angular/router';
import { InvestorService } from '../../shared/services/investor.service';
import { AuthService } from '../../shared/services/auth.service';
import { Investment, Investor } from '../../shared/models/investor.model';

@Component({
  selector: 'app-investment-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, RouterLinkActive],
  templateUrl: './investment-detail.html',
  styleUrl: './investment-detail.scss'
})
export class InvestmentDetailComponent implements OnInit {
  investment: Investment | null = null;
  isNew = false;
  saved = false;
  error = '';
  docError = '';
  allInvestors: Investor[] = [];
  selectedInvestorIds: Set<number> = new Set();
  newDoc = { name: '', file: null as File | null, url: '' };
  docMode: 'upload' | 'link' = 'upload';
  uploading = false;
  editingDocId: number | null = null;
  editDocName = '';

  form: any = {
    name: '', startDate: '', anticipatedEndDate: '', endDate: '',
    currentUpdate: '', projectProgress: 0
  };

  constructor(
    private route: ActivatedRoute,
    private investorService: InvestorService,
    private authService: AuthService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.investorService.getAllInvestors().subscribe(data => {
      this.allInvestors = data;
      this.cdr.detectChanges();
    });

    const id = this.route.snapshot.paramMap.get('id');
    if (id === 'new') {
      this.isNew = true;
    } else {
      this.investorService.getInvestmentById(Number(id)).subscribe(data => {
        this.investment = data;
        this.form = {
          name: data.name,
          startDate: data.startDate,
          anticipatedEndDate: data.anticipatedEndDate ?? '',
          endDate: data.endDate ?? '',
          currentUpdate: data.currentUpdate ?? '',
          projectProgress: data.projectProgress ?? 0
        };
        this.selectedInvestorIds = new Set(data.investors.map(i => i.investorId));
        this.cdr.detectChanges();
      });
    }
  }

  toggleInvestor(id: number): void {
    if (this.selectedInvestorIds.has(id)) {
      this.selectedInvestorIds.delete(id);
    } else {
      this.selectedInvestorIds.add(id);
    }
    this.cdr.detectChanges();
  }

  isSelected(id: number): boolean {
    return this.selectedInvestorIds.has(id);
  }

  save(): void {
    this.saved = false;
    this.error = '';
    const payload = {
      name: this.form.name,
      startDate: this.form.startDate,
      anticipatedEndDate: this.form.anticipatedEndDate || null,
      endDate: this.form.endDate || null,
      currentUpdate: this.form.currentUpdate || null,
      projectProgress: this.form.projectProgress ?? 0,
      investorIds: Array.from(this.selectedInvestorIds)
    };

    const call = this.isNew
      ? this.investorService.createInvestment(payload)
      : this.investorService.updateInvestment(this.investment!.investmentId, payload);

    call.subscribe({
      next: data => {
        this.investment = data;
        this.saved = true;
        this.cdr.detectChanges();
        this.isNew = false;
      },
      error: () => {
        this.error = 'Failed to save. Please check the form and try again.';
        this.cdr.detectChanges();
      }
    });
  }

  startEdit(doc: { documentId: number; name: string; url: string }): void {
    this.editingDocId = doc.documentId;
    this.editDocName = doc.name;
    this.cdr.detectChanges();
  }

  cancelEdit(): void {
    this.editingDocId = null;
    this.cdr.detectChanges();
  }

  saveEdit(documentId: number): void {
    this.docError = '';
    if (!this.editDocName.trim()) {
      this.docError = 'Document name is required.';
      this.cdr.detectChanges();
      return;
    }
    this.investorService.updateDocument(this.investment!.investmentId, documentId, { name: this.editDocName }).subscribe({
      next: (data: any) => {
        this.investment = data;
        this.editingDocId = null;
        this.cdr.detectChanges();
      },
      error: () => {
        this.docError = 'Failed to update document.';
        this.cdr.detectChanges();
      }
    });
  }

  onFileSelect(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.newDoc.file = input.files?.[0] ?? null;
  }

  addDocument(): void {
    this.docError = '';
    if (this.docMode === 'upload') {
      if (!this.newDoc.name.trim() || !this.newDoc.file) {
        this.docError = 'Document name and file are required.';
        this.cdr.detectChanges();
        return;
      }
      this.uploading = true;
      this.investorService.uploadDocument(this.investment!.investmentId, this.newDoc.name, this.newDoc.file!).subscribe({
        next: (data: any) => {
          this.investment = data;
          this.newDoc = { name: '', file: null, url: '' };
          this.uploading = false;
          this.cdr.detectChanges();
        },
        error: () => {
          this.docError = 'Failed to upload document.';
          this.uploading = false;
          this.cdr.detectChanges();
        }
      });
    } else {
      if (!this.newDoc.name.trim() || !this.newDoc.url.trim()) {
        this.docError = 'Document name and URL are required.';
        this.cdr.detectChanges();
        return;
      }
      this.uploading = true;
      this.investorService.linkDocument(this.investment!.investmentId, this.newDoc.name, this.newDoc.url).subscribe({
        next: (data: any) => {
          this.investment = data;
          this.newDoc = { name: '', file: null, url: '' };
          this.uploading = false;
          this.cdr.detectChanges();
        },
        error: () => {
          this.docError = 'Failed to link document.';
          this.uploading = false;
          this.cdr.detectChanges();
        }
      });
    }
  }

  removeDocument(documentId: number): void {
    if (!confirm('Remove this document?')) return;
    this.investorService.removeDocument(this.investment!.investmentId, documentId).subscribe({
      next: () => {
        this.investment!.documents = this.investment!.documents.filter(d => d.documentId !== documentId);
        this.cdr.detectChanges();
      },
      error: () => {
        this.docError = 'Failed to remove document.';
        this.cdr.detectChanges();
      }
    });
  }

  logout(): void { this.authService.logout(); }
}
