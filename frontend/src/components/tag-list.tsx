type TagListProps = {
  className?: string;
  emptyLabel?: string;
  items: string[];
  label: string;
};

export function TagList({
  className,
  emptyLabel,
  items,
  label
}: TagListProps) {
  if (items.length === 0 && !emptyLabel) {
    return null;
  }

  const classes = ["tag-list", className].filter(Boolean).join(" ");

  return (
    <div className={classes}>
      <p>{label}</p>
      {items.length > 0 ? (
        <div className="tag-list-items">
          {items.map((item) => (
            <span className="tag-pill" key={item}>
              {item}
            </span>
          ))}
        </div>
      ) : (
        <span className="tag-pill">{emptyLabel}</span>
      )}
    </div>
  );
}
