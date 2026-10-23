import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, Navigate } from 'react-router-dom';
import { z } from 'zod';

import { useAuth } from '../../auth/auth-context';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Field';
import { api } from '../../lib/api';
import { errorMessage } from '../../lib/errors';
import { AuthLayout } from './AuthLayout';

// Las mismas reglas que PasswordPolicy en el backend (que es quien manda): aquí
// solo sirven para avisar antes de enviar.
const schema = z
  .object({
    displayName: z.string().trim().max(100, 'Máximo 100 caracteres').optional(),
    email: z.email('Introduce un email válido').max(255),
    password: z.string().min(10, 'Al menos 10 caracteres'),
    confirm: z.string(),
  })
  .refine((v) => v.password === v.confirm, {
    path: ['confirm'],
    message: 'Las contraseñas no coinciden',
  })
  .refine((v) => v.password.toLowerCase() !== v.email.toLowerCase(), {
    path: ['password'],
    message: 'No puede ser igual al email',
  });
type FormValues = z.infer<typeof schema>;

export function RegisterPage() {
  const { user, login } = useAuth();
  const [serverError, setServerError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  // Justo después de registrarse, login() ya ha puesto el usuario y este
  // Navigate es el que redirige: a Cuentas, que es lo primero que hay que crear.
  if (user) return <Navigate to="/accounts" replace />;

  const onSubmit = handleSubmit(async ({ displayName, email, password }) => {
    setServerError(null);
    try {
      await api('/api/v1/auth/register', {
        method: 'POST',
        body: { email, password, displayName: displayName || undefined },
        auth: false,
      });
      await login(email, password);
    } catch (error) {
      setServerError(errorMessage(error));
    }
  });

  return (
    <AuthLayout
      title="Crea tu cuenta"
      subtitle="Empieza añadiendo tus cuentas y tus primeros movimientos."
    >
      <form onSubmit={onSubmit} noValidate className="space-y-4">
        {serverError && <Alert>{serverError}</Alert>}
        <Input
          label="Nombre"
          optional
          autoComplete="name"
          error={errors.displayName?.message}
          {...register('displayName')}
        />
        <Input
          label="Email"
          type="email"
          autoComplete="email"
          error={errors.email?.message}
          {...register('email')}
        />
        <Input
          label="Contraseña"
          type="password"
          autoComplete="new-password"
          hint="Al menos 10 caracteres."
          error={errors.password?.message}
          {...register('password')}
        />
        <Input
          label="Repite la contraseña"
          type="password"
          autoComplete="new-password"
          error={errors.confirm?.message}
          {...register('confirm')}
        />
        <Button type="submit" loading={isSubmitting} className="w-full">
          Crear cuenta
        </Button>
      </form>
      <p className="mt-6 text-center text-sm text-muted">
        ¿Ya tienes cuenta?{' '}
        <Link to="/login" className="font-medium text-brand underline-offset-2 hover:underline">
          Inicia sesión
        </Link>
      </p>
    </AuthLayout>
  );
}
