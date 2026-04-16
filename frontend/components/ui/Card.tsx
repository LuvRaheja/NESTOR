import { ReactNode, HTMLAttributes } from 'react';

type Tier = 'flat' | 'raised' | 'elevated';

interface CardProps extends HTMLAttributes<HTMLDivElement> {
  tier?: Tier;
  children: ReactNode;
  padding?: string;
}

const tierStyles: Record<Tier, string> = {
  flat:     'border border-border',
  raised:   'border border-border shadow-sm',
  elevated: 'shadow-lg',
};

export default function Card({ tier = 'raised', children, padding = 'p-6', className = '', ...props }: CardProps) {
  return (
    <div
      className={`bg-card rounded-xl ${tierStyles[tier]} ${padding} ${className}`}
      {...props}
    >
      {children}
    </div>
  );
}
