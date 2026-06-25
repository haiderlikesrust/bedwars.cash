type SolIconProps = {
  size?: number;
  className?: string;
};

export function SolIcon({ size = 20, className = '' }: SolIconProps) {
  return (
    <img
      src="/sol.png"
      alt=""
      width={size}
      height={size}
      className={['sol-icon', className].filter(Boolean).join(' ')}
      aria-hidden="true"
    />
  );
}

type SolUnitProps = {
  size?: number;
  className?: string;
};

/** Inline Solana logo + "SOL" label. */
export function SolUnit({ size = 16, className = '' }: SolUnitProps) {
  return (
    <span className={['sol-unit', className].filter(Boolean).join(' ')}>
      <SolIcon size={size} />
      <span>SOL</span>
    </span>
  );
}

type SolAmountProps = {
  amount: number;
  decimals?: number;
  iconSize?: number;
  className?: string;
};

/** Formatted amount with Solana logo and SOL suffix. */
export function SolAmount({ amount, decimals = 4, iconSize = 14, className = '' }: SolAmountProps) {
  return (
    <span className={['sol-amount', className].filter(Boolean).join(' ')}>
      {amount.toFixed(decimals)}
      <SolUnit size={iconSize} />
    </span>
  );
}
