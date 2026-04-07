import React from "react";
import { Outlet } from "react-router-dom";

import Header from "./Header";

interface MainProps {
  children?: React.ReactNode;
}

const Main = ({ children }: MainProps) => {
  return (
    <div className="layout">
      <Header />
      <main className="layout__content">{children ?? <Outlet />}</main>
      {/* <Footer /> */}
    </div>
  );
};

export default Main;
