type LogoProps = {
  width?: number | string;
  height?: number | string;
  className?: string;
};

const Logo = ({ width = 36, height = 42, className }: LogoProps) => {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width={width}
      height={height}
      viewBox="0 0 104 121"
      fill="none"
      className={className}
      aria-hidden="true"
    >
      <path
        fillRule="evenodd"
        clipRule="evenodd"
        d="M104 18.1504V55.0547C104 70.3814 99.0989 84.3213 89.2969 96.873C79.4906 109.429 67.0582 117.471 52 121C36.9417 117.471 24.511 109.429 14.709 96.873C4.90267 84.3213 0 70.3814 0 55.0547V18.1504L52 0L104 18.1504ZM13 26.4688V55.0547C13 67.2555 16.6832 78.3476 24.0498 88.3301C31.4165 98.3126 40.7334 104.967 52 108.295C63.2666 104.967 72.5836 98.3125 79.9502 88.3301C87.3168 78.3476 91 67.2555 91 55.0547V26.4688L52 12.8564L13 26.4688Z"
        fill="currentColor"
      />
      <path
        d="M83.5576 62.6553C81.0845 80.3547 68.4345 95.8824 52 100C46.749 98.6844 41.8854 96.2023 37.6152 92.8584L83.5576 62.6553Z"
        fill="currentColor"
      />
      <path
        d="M84 56.3604C84 56.7708 83.9917 57.1806 83.9805 57.5898L34.4688 90.1396C31.9171 87.71 29.6364 84.9523 27.6807 81.9482L84 44.4023V56.3604Z"
        fill="currentColor"
      />
      <path
        d="M84 32V39.5957L25.6387 78.5029C22.0538 71.8279 20.0001 64.1967 20 56.3604V32L52 20L84 32Z"
        fill="currentColor"
      />
    </svg>
  );
};

export default Logo;
