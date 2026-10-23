import type { InputHTMLAttributes, Ref, SelectHTMLAttributes } from 'react';

const CONTROL =
  'block w-full rounded-lg border-0 bg-surface px-3 py-2 text-sm text-ink ring-1 ring-inset placeholder:text-muted/70 focus:ring-2 focus:ring-brand focus:outline-none disabled:bg-paper';

interface FieldWrapperProps {
  id: string;
  label: string;
  error?: string | undefined;
  hint?: string;
  optional?: boolean;
  className?: string;
  children: React.ReactNode;
}

/** Etiqueta, control y mensaje de error enlazados con for/id y aria-describedby. */
function FieldWrapper({
  id,
  label,
  error,
  hint,
  optional,
  className = '',
  children,
}: FieldWrapperProps) {
  return (
    <div className={className}>
      <label htmlFor={id} className="mb-1 block text-sm font-medium text-ink">
        {label}
        {optional && <span className="ml-1 font-normal text-muted">(opcional)</span>}
      </label>
      {children}
      {error ? (
        <p id={`${id}-error`} className="mt-1 text-xs text-expense">
          {error}
        </p>
      ) : (
        hint && (
          <p id={`${id}-hint`} className="mt-1 text-xs text-muted">
            {hint}
          </p>
        )
      )}
    </div>
  );
}

const describedBy = (id: string, error?: string, hint?: string) =>
  error ? `${id}-error` : hint ? `${id}-hint` : undefined;

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  error?: string | undefined;
  hint?: string;
  optional?: boolean;
  ref?: Ref<HTMLInputElement>;
}

export function Input({ label, error, hint, optional, id, className, ref, ...rest }: InputProps) {
  const inputId = id ?? rest.name ?? label;
  return (
    <FieldWrapper id={inputId} {...{ label, error, hint, optional, className }}>
      <input
        id={inputId}
        ref={ref}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(inputId, error, hint)}
        className={`${CONTROL} ${error ? 'ring-expense' : 'ring-line'}`}
        {...rest}
      />
    </FieldWrapper>
  );
}

interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label: string;
  error?: string | undefined;
  hint?: string;
  optional?: boolean;
  ref?: Ref<HTMLSelectElement>;
}

export function Select({
  label,
  error,
  hint,
  optional,
  id,
  className,
  ref,
  children,
  ...rest
}: SelectProps) {
  const selectId = id ?? rest.name ?? label;
  return (
    <FieldWrapper id={selectId} {...{ label, error, hint, optional, className }}>
      <select
        id={selectId}
        ref={ref}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(selectId, error, hint)}
        className={`${CONTROL} ${error ? 'ring-expense' : 'ring-line'}`}
        {...rest}
      >
        {children}
      </select>
    </FieldWrapper>
  );
}
