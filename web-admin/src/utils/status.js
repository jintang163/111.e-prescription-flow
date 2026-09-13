export const RX_STATUS = {
  DRAFT: '草稿', SUBMITTED: '已提交', REVIEWING: '审核中', REJECTED: '已驳回',
  AMENDMENT_REQUESTED: '待补正', EFFECTIVE: '已生效', DISPATCHING: '派药中',
  DISPATCHED: '已下发药店', FULFILLING: '配药中', DISPENSED: '已配药',
  READY_FOR_PICKUP: '待取药', PICKED_UP: '已取药', TRANSFER_FAILED: '流转异常', CANCELLED: '已取消'
}
export const FULFILLMENT_STATUS = {
  STOCK_CHECKING: '库存校验', STOCK_HELD: '已预占', ORDER_PLACED: '已下单',
  PHARMACY_ACCEPTED: '药店已受理', DISPENSING: '配药中', DISPENSED: '已配齐',
  DELIVERING: '配送中', READY_FOR_PICKUP: '待取药', PICKED_UP: '已取药',
  REJECTED: '拒绝/缺货', CANCELLED: '已取消'
}
export const statusText = s => RX_STATUS[s] || s
export const fulfillmentText = s => FULFILLMENT_STATUS[s] || s
export const statusType = s => ({
  DRAFT: 'info', SUBMITTED: 'info', REVIEWING: 'warning', REJECTED: 'danger',
  AMENDMENT_REQUESTED: 'warning', EFFECTIVE: 'success', DISPATCHING: 'warning',
  DISPATCHED: 'primary', FULFILLING: 'primary', DISPENSED: 'primary',
  READY_FOR_PICKUP: 'success', PICKED_UP: 'success', TRANSFER_FAILED: 'danger', CANCELLED: 'info'
}[s] || 'info')
