type IconScrollBottomProps = {
  width?: number | string;
  height?: number | string;
  color?: string;
  className?: string;
};

const IconScrollBottom = ({
  width = 24,
  height = 16,
  color = "currentColor",
  className,
}: IconScrollBottomProps) => {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width={width}
      height={height}
      viewBox="0 0 48 30"
      fill="none"
      className={className}
      aria-hidden="true"
    >
      <path
        d="M5.64 29.64L24 11.32L42.36 29.64L48 24L24 0L0 24L5.64 29.64Z"
        fill={color}
      />
    </svg>
  );
};

export default IconScrollBottom;
