import { ReactNode, useEffect, useRef, useCallback } from 'react';
import Button from './ui/Button';

interface ConfirmModalProps {
  isOpen: boolean;
  title: string;
  message: string | ReactNode;
  confirmText?: string;
  cancelText?: string;
  confirmButtonClass?: string;
  onConfirm: () => void;
  onCancel: () => void;
  isProcessing?: boolean;
}

export default function ConfirmModal({
  isOpen,
  title,
  message,
  confirmText = 'Confirm',
  cancelText = 'Cancel',
  confirmButtonClass,
  onConfirm,
  onCancel,
  isProcessing = false,
}: ConfirmModalProps) {
  const dialogRef = useRef<HTMLDivElement>(null);
  const previousFocusRef = useRef<HTMLElement | null>(null);

  const trapFocus = useCallback((e: KeyboardEvent) => {
    if (e.key === 'Escape') {
      onCancel();
      return;
    }
    if (e.key !== 'Tab' || !dialogRef.current) return;
    const focusable = dialogRef.current.querySelectorAll<HTMLElement>(
      'button:not([disabled]), [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
    );
    if (focusable.length === 0) return;
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (e.shiftKey && document.activeElement === first) {
      e.preventDefault();
      last.focus();
    } else if (!e.shiftKey && document.activeElement === last) {
      e.preventDefault();
      first.focus();
    }
  }, [onCancel]);

  useEffect(() => {
    if (isOpen) {
      previousFocusRef.current = document.activeElement as HTMLElement;
      document.addEventListener('keydown', trapFocus);
      // Focus the dialog after render
      requestAnimationFrame(() => {
        const firstBtn = dialogRef.current?.querySelector<HTMLElement>('button');
        firstBtn?.focus();
      });
    }
    return () => {
      document.removeEventListener('keydown', trapFocus);
      previousFocusRef.current?.focus();
    };
  }, [isOpen, trapFocus]);

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className="bg-card rounded-xl max-w-md w-full p-6 shadow-xl border border-border"
      >
        <div className="mb-4">
          <h3 className="text-xl font-bold text-dark">{title}</h3>
        </div>

        <div className="mb-6 text-gray-600">
          {message}
        </div>

        <div className="flex gap-3">
          <Button
            onClick={onCancel}
            disabled={isProcessing}
            variant="secondary"
            className="flex-1"
          >
            {cancelText}
          </Button>
          <Button
            onClick={onConfirm}
            disabled={isProcessing}
            loading={isProcessing}
            variant="danger"
            className={`flex-1 ${confirmButtonClass || ''}`}
          >
            {isProcessing ? 'Processing...' : confirmText}
          </Button>
        </div>
      </div>
    </div>
  );
}