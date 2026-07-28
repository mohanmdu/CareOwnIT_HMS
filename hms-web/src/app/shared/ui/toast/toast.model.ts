export type ToastTone = 'success' | 'error' | 'warning' | 'info' | 'loading';

export interface ToastConfig {
  id: string;
  tone: ToastTone;
  title: string;
  message: string;
  /** ms until auto-dismiss; undefined/0 means it never auto-dismisses (used by 'loading' until the caller calls dismiss()/updateProgress() -> a terminal tone). */
  duration?: number;
  /** 0-100 for a determinate progress bar (loading tone only) - undefined renders an indeterminate bar. */
  progress?: number;
}
