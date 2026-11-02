export type Provider = { name: string; email: string; bookingSlug: string };
export type ServiceOffering = { id: number; name: string; duration: number };
export type Availability = { id: number; date: string; startTime: string; endTime: string };
export type Appointment = {
  id: number;
  serviceOffering: ServiceOffering;
  customerName: string;
  customerEmail: string;
  customerPhone: string;
  startTime: string;
  endTime: string;
  status: 'BOOKED' | 'CANCELLED';
};
