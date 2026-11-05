import { createContext, useContext } from 'react';

/** Muestra un aviso breve de que algo se ha hecho ("Movimiento guardado"). */
export type Notify = (message: string) => void;

export const NotifyContext = createContext<Notify>(() => {});

export const useNotify = () => useContext(NotifyContext);
