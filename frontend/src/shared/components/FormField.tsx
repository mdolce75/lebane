import type { ReactNode } from 'react';

type Props = {
  label: string;
  htmlFor: string;
  error?: string;
  hint?: string;
  required?: boolean;
  className?: string;
  children: ReactNode;
};

/** Campo de formulario accesible: label asociado, ayuda y error anunciado a lectores de pantalla. */
export function FormField({ label, htmlFor, error, hint, required, className, children }: Props) {
  return (
    <div className={`field ${error ? 'field--invalid' : ''} ${className ?? ''}`}>
      <label htmlFor={htmlFor}>
        {label}
        {required && <span aria-hidden="true" className="field__required"> *</span>}
      </label>
      {children}
      {hint && !error && <span className="field__hint">{hint}</span>}
      {error && (
        <span className="field__error" role="alert" id={`${htmlFor}-error`}>
          {error}
        </span>
      )}
    </div>
  );
}
