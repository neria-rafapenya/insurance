type IconBackProps = {
  width?: number | string;
  height?: number | string;
  color?: string;
  className?: string;
};

const IconBack = ({
  width = 18,
  height = 12,
  color = "currentColor",
  className,
}: IconBackProps) => {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width={width}
      height={height}
      viewBox="0 0 18 12"
      fill="none"
      className={className}
      aria-hidden="true"
    >
      <path
        d="M7.40039 1.40039L3.7998 5H18V7H3.7998L7.40039 10.5996L6 12L0 6L6 0L7.40039 1.40039Z"
        fill={color}
      />
    </svg>
  );
};

export default IconBack;
