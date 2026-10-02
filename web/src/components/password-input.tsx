"use client";

import { useState } from "react";

type PasswordInputProps = {
  id: string;
  name: string;
  label: string;
  autoComplete: string;
  minLength?: number;
};

export function PasswordInput({
  id,
  name,
  label,
  autoComplete,
  minLength,
}: PasswordInputProps) {
  const [visible, setVisible] = useState(false);
  const actionLabel = visible ? "Hide password" : "Show password";

  return (
    <div className="admin-password-field">
      <label htmlFor={id}>{label}</label>
      <div className="admin-password-input-wrap">
        <input
          className="admin-password-input"
          id={id}
          name={name}
          type={visible ? "text" : "password"}
          autoComplete={autoComplete}
          minLength={minLength}
          required
        />
        <button
          className="admin-password-toggle"
          type="button"
          aria-label={actionLabel}
          aria-pressed={visible}
          aria-controls={id}
          title={actionLabel}
          onClick={() => setVisible(!visible)}
        >
          <svg
            aria-hidden="true"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.8"
            strokeLinecap="round"
            strokeLinejoin="round"
          >
            <path d="M2.5 12s3.5-6.5 9.5-6.5 9.5 6.5 9.5 6.5-3.5 6.5-9.5 6.5S2.5 12 2.5 12Z" />
            <circle cx="12" cy="12" r="3" />
            {visible && <path d="m4 4 16 16" />}
          </svg>
        </button>
      </div>
    </div>
  );
}