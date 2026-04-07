import { createBrowserRouter } from "react-router-dom";

import Login from "./auth/Login";
import PrivateRoute from "./auth/PrivateRoute";
import Main from "./layout/Main";
import PromptsAdmin from "./pages/admin/PromptsAdmin";
import RiskFactorsAdmin from "./pages/admin/RiskFactorsAdmin";
import Wizard from "./pages/Wizard";

export const router = createBrowserRouter([
  {
    path: "/login",
    element: <Login />,
  },
  {
    path: "/",
    element: <Main />,
    children: [
      {
        element: <PrivateRoute />,
        children: [
          {
            index: true,
            element: <Wizard />,
          },
          {
            path: "admin/risk-factors",
            element: <RiskFactorsAdmin />,
          },
          {
            path: "admin/prompts",
            element: <PromptsAdmin />,
          },
        ],
      },
    ],
  },
]);
