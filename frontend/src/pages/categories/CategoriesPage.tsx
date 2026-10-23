import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { z } from 'zod';

import { useCategories, useCreateCategory, useDeleteCategory } from '../../api/hooks';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { Input, Select } from '../../components/ui/Field';
import { Modal } from '../../components/ui/Modal';
import { PageHeader } from '../../components/ui/PageHeader';
import { ErrorState, LoadingState } from '../../components/ui/States';
import { errorMessage } from '../../lib/errors';
import { CATEGORY_KIND_LABELS } from '../../lib/labels';
import type { Category, CategoryKind } from '../../types/api';

const schema = z.object({
  name: z.string().trim().min(1, 'Ponle un nombre').max(60, 'Máximo 60 caracteres'),
  kind: z.enum(['EXPENSE', 'INCOME']),
  parentId: z.string(),
});
type FormValues = z.infer<typeof schema>;

function CreateCategoryModal({
  categories,
  onClose,
}: {
  categories: Category[];
  onClose: () => void;
}) {
  const create = useCreateCategory();
  const {
    register,
    handleSubmit,
    control,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { name: '', kind: 'EXPENSE', parentId: '' },
  });

  // Una subcategoría tiene que ser del mismo tipo que su padre, y solo hay dos niveles.
  const kind = useWatch({ control, name: 'kind' });
  const parents = categories.filter((c) => c.kind === kind && !c.parentId);

  const onSubmit = handleSubmit(({ parentId, ...rest }) =>
    create.mutate({ ...rest, parentId: parentId || undefined }, { onSuccess: onClose }),
  );

  return (
    <Modal title="Nueva categoría" onClose={onClose}>
      <form onSubmit={onSubmit} noValidate className="space-y-4">
        {create.isError && <Alert>{errorMessage(create.error)}</Alert>}
        <Input label="Nombre" error={errors.name?.message} {...register('name')} />
        <div className="grid gap-4 sm:grid-cols-2">
          <Select label="Tipo" {...register('kind')}>
            {Object.entries(CATEGORY_KIND_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </Select>
          <Select label="Dentro de" optional {...register('parentId')}>
            <option value="">Ninguna (principal)</option>
            {parents.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </Select>
        </div>
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose}>
            Cancelar
          </Button>
          <Button type="submit" loading={create.isPending}>
            Crear categoría
          </Button>
        </div>
      </form>
    </Modal>
  );
}

function CategoryGroup({
  kind,
  categories,
  onDelete,
}: {
  kind: CategoryKind;
  categories: Category[];
  onDelete: (c: Category) => void;
}) {
  const roots = categories.filter((c) => c.kind === kind && !c.parentId);
  const childrenOf = (id: string) => categories.filter((c) => c.parentId === id);

  const row = (category: Category, child = false) => (
    <li
      key={category.id}
      className={`flex items-center justify-between gap-3 py-2 ${child ? 'pl-5' : ''}`}
    >
      <span
        className={`flex min-w-0 items-center gap-2 text-sm ${child ? 'text-muted' : 'font-medium'}`}
      >
        <span
          aria-hidden="true"
          className="size-2.5 shrink-0 rounded-full"
          style={{ backgroundColor: category.color ?? 'var(--color-line)' }}
        />
        <span className="truncate">{category.name}</span>
      </span>
      {category.system ? (
        <span className="text-xs text-muted">Del sistema</span>
      ) : (
        <Button
          variant="ghost"
          aria-label={`Eliminar la categoría ${category.name}`}
          onClick={() => onDelete(category)}
        >
          Eliminar
        </Button>
      )}
    </li>
  );

  return (
    <section className="rounded-xl bg-surface p-5 ring-1 ring-line">
      <h2 className="mb-2 text-sm font-semibold">{kind === 'EXPENSE' ? 'Gastos' : 'Ingresos'}</h2>
      <ul className="divide-y divide-line">
        {roots.map((root) => (
          <li key={root.id}>
            <ul>
              {row(root)}
              {childrenOf(root.id).map((child) => row(child, true))}
            </ul>
          </li>
        ))}
      </ul>
    </section>
  );
}

export default function CategoriesPage() {
  const { data: categories, isPending, isError, error, refetch } = useCategories();
  const deleteCategory = useDeleteCategory();
  const [creating, setCreating] = useState(false);
  const [toDelete, setToDelete] = useState<Category | null>(null);

  const askDelete = (category: Category) => {
    deleteCategory.reset();
    setToDelete(category);
  };

  return (
    <>
      <PageHeader
        title="Categorías"
        description="Las del sistema sirven para empezar; puedes añadir las tuyas y subcategorías."
        actions={<Button onClick={() => setCreating(true)}>Nueva categoría</Button>}
      />

      {isPending ? (
        <LoadingState />
      ) : isError ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : (
        <div className="grid gap-6 lg:grid-cols-2">
          <CategoryGroup kind="EXPENSE" categories={categories} onDelete={askDelete} />
          <CategoryGroup kind="INCOME" categories={categories} onDelete={askDelete} />
        </div>
      )}

      {creating && categories && (
        <CreateCategoryModal categories={categories} onClose={() => setCreating(false)} />
      )}
      {toDelete && (
        <ConfirmDialog
          title="Eliminar categoría"
          message={`Se elimina «${toDelete.name}». Si tiene movimientos, presupuestos o subcategorías, no se podrá eliminar.`}
          confirmText="Eliminar"
          danger
          loading={deleteCategory.isPending}
          error={deleteCategory.isError ? errorMessage(deleteCategory.error) : null}
          onCancel={() => setToDelete(null)}
          onConfirm={() =>
            deleteCategory.mutate(toDelete.id, { onSuccess: () => setToDelete(null) })
          }
        />
      )}
    </>
  );
}
